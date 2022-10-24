import scodec.bits.*
import sttp.client3.httpclient.zio.HttpClientZioBackend
import zio.*
import zio.Console.*
import zio.stream.*

object lnz extends ZIOAppDefault:

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
  import perun.crypto.publicKey
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
  ): ZIO[Secp256k1 & Keygen, Object, (Peer, ByteVector)] =
    read
      .via(collectByLengths(50, 66))
      .map(c => ByteVector.apply(c.toArray))
      // .tap(b => putStrLn(b.toString))
      .runFoldWhileZIO(
        (Right(init): Either[Peer, HandshakeState], Option.empty[ByteVector])
      )(_._2.isEmpty) {
        case ((l @ Left(_), _), m) =>
          // println("1 @@@@ " + l)
          ZIO.succeed((l, Some(m)))
        case ((Right(st), _), m) =>
          for
            x <- st.readMessage(m)
            // _ = println("2 @@@@ " + x + " / " + m)
            y <- x match
              case HandshakeResult.Done(c1, c2) =>
                // println("3 @@@@ " + x + " / " + m)
                ZIO.succeed((Left(Peer(c1, c2)), None))

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
    Any,
    Throwable,
    Secp256k1 & Rpc & P2P & Keygen & perun.db.store.Store
  ] =
    perun.crypto.keygen.liveKeygen ++
      store.live("jdbc:hsqldb:file:testdb") ++
      (Runtime.removeDefaultLoggers >>> zio.logging.console(
        zio.logging.LogFormat.colored
      )) ++
      P2P.inMemory ++
      (
        HttpClientZioBackend.layer() >>>
          bitcoind(
            uri"http://127.0.0.1:18443",
            "polaruser",
            "polarpass"
          )
      ) ++
      native.mapError(e => new Exception(e.toString))

  // Polar: 172.17.0.1

  def run =
    for
      _ <- Console.printLine(ls1.publicKey)
      h <- Hub.unbounded[(Promise[String, String], String)]
      _ <- ZStream
        .fromHub(h)
        .foreach((p, s) => p.succeed(s.map(_.toUpper)))
        .fork
      _ <- ZStream
        .fromSocketServer(9988, None)
        .foreach(c =>
          Console.printLine(">>> " + c) *> c.read
            .via(ZPipeline.utf8Decode >>> ZPipeline.splitLines)
            .foreach { line =>
              for
                p <- Promise.make[String, String]
                _ <- h.publish((p, line)).fork
                s <- p.await
                _ <- ZStream(s + '\n').via(ZPipeline.utf8Encode).run(c.write)
                _ <- c.closeWrite()
              yield ()
            }
            .fork
        )
        .fork
      e <- ZStream
        .fromSocketServer(9977, None)
        .foreach { c =>
          handshake(responder, c.read, c.write)
            .flatMap { (peer, leftover) =>
              perun.peer
                .start(
                  perun.peer.Configuration(
                    perun.proto.blockchain.Chain.Regtest
                  ),
                  ZStream.fromIterable(leftover.toArray) ++ c.read,
                  c.write,
                  c.closeWrite(),
                  peer.rk,
                  peer.sk
                )
                .ensuring(c.closeWrite().orDie)
                .fork
            }
        }
        .onInterrupt(ZIO.succeedBlocking(println("DOOONE")))
        .provideSomeLayer(dep)
        .provideEnvironment(DefaultServices.live)
        .exitCode
    yield e
end lnz
