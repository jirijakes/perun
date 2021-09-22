import scodec.bits.*
import sttp.client3.httpclient.zio.HttpClientZioBackend
import zio.*
import zio.console.*
import zio.stream.*

object lnz extends App:

  val ls1 = perun.crypto.PrivateKey.fromHex(
    "1111111111111111111111111111111111111111111111111111111111111111"
  )

  val ls2 = perun.crypto.PrivateKey.fromHex(
    "2121212121212121212121212121212121212121212121212121212121212121"
  )

  val rs = perun.crypto.PublicKey.fromHex(
    "028d7500dd4c12685d1f568b4c2b5048e8534b873319f3a8daa612b469132ec7f7"
  )

  import noise.*
  import perun.crypto.keygen.*
  import perun.crypto.secp256k1.*
  import util.*

  import perun.db.*
  import perun.db.p2p.*
  import perun.net.rpc.*

  val initiator = HandshakeState.initiator(rs, ls1)
  val responder = HandshakeState.responder(ls1)

  // val prg: ZIO[Keygen & Console, HandshakeError, ExitCode] =
  //   for
  //     i <- RefM.make(initiator)
  //     r <- RefM.make(responder)
  //     // x1 <- i.modify(_.writeMessage(ByteVector.empty))
  //     _ <- r.modify(_.readMessage(x1))
  //     // _ <- putStrLn(x1.toString)
  //     x2 <- r.modify(_.writeMessage(ByteVector.empty))
  //     // _ <- putStrLn(x2.toString)
  //     // _ <- i.modify(_.readMessage(x2))
  //     // x3 <- i.modify(_.writeMessage(ByteVector.empty))
  //     // _ <- putStrLn(x3.toString)
  //     _ <- r.modify(_.readMessage(x3))
  //   yield ExitCode.success

  final case class Peer(rk: CipherState, sk: CipherState)

  def handshake(
      init: HandshakeState,
      read: Stream[Throwable, Byte],
      write: Sink[Throwable, Byte, Nothing, Int]
  ) =
    read
      .transduce(collectByLengths(50, 66))
      .map(c => ByteVector.apply(c.toArray))
      // .tap(b => putStrLn(b.toString))
      .foldWhileM(
        (Right(init): Either[Peer, HandshakeState], Option.empty[ByteVector])
      )(_._2.isEmpty) {
        case ((l @ Left(_), _), m) =>
          // println("1 @@@@ " + l)
          UIO((l, Some(m)))
        case ((Right(st), _), m) =>
          for
            x <- st.readMessage(m)
            // _ = println("2 @@@@ " + x + " / " + m)
            y <- x match
              case HandshakeResult.Done(c1, c2) =>
                // println("3 @@@@ " + x + " / " + m)
                UIO.succeed((Left(Peer(c1, c2)), None))

              case HandshakeResult.Continue(_, s) =>
                // println("4 @@@@ " + x + " / " + m)
                s.writeMessage(ByteVector.empty).flatMap {
                  case HandshakeResult.Done(_, _) =>
                    // println("5 @@@@ ")
                    ??? // st
                  case HandshakeResult.Continue(m, s) =>
                    // println("6 @@@@ " + x + " / " + m)
                    ZStream
                      .fromIterable(m.toArray)
                      .run(write)
                      // .tap(x => putStrLn("7 >>>>>> " + x))
                      .as((Right(s), None))
                }
          yield y
      }
      // .tap(xxx => ZIO.effectTotal(println("###> " + xxx)))
      .collect("BOOOM") { case (Left(m), Some(n)) => (m, n) }

  val dep: ZLayer[
    zio.blocking.Blocking,
    Nothing,
    Has[Secp256k1] & Has[Rpc] & Has[P2P] &
      Has[Keygen] & Has[perun.db.store.Store]
  ] =
    perun.crypto.keygen.liveKeygen ++
      store.live("jdbc:hsqldb:file:testdb").orDie ++
      P2P.inMemory ++
      (
        HttpClientZioBackend.layer().orDie >>>
          bitcoind(
            uri"http://10.0.0.21:18332",
            "__cookie__",
            "b1ff31caa6d6324557d010d2f8257d5dc9e9f90222281b97a51605f2460ebb4c"
          ).orDie
      ) ++
      native.mapError(e => new Exception(e.toString)).orDie

  def run(args: List[String]): URIO[ZEnv, ExitCode] =
    ZStream
      .fromSocketServer(9977, None)
      .foreach { c =>
        handshake(responder, c.read, c.write)
          .flatMap { (peer, leftover) =>
            perun.peer
              .start(
                perun.peer.Configuration(perun.proto.blockchain.Chain.Testnet),
                Stream.fromIterable(leftover.toArray) ++ c.read,
                c.write,
                c.close(),
                peer.rk,
                peer.sk
              )
              .ensuring(c.close())
              .fork
          }
      }
      .onInterrupt(ZIO.effectTotal(println("DOOONE")))
      .provideCustomLayer(dep)
      .exitCode

end lnz
