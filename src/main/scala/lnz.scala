import scodec.bits.*
import zio.*
import zio.console.*
import zio.stream.*

object lnz extends App:

  val x = org.bitcoins.crypto.ECPublicKey.dummy

  System.loadLibrary("secp256k1")
  org.scijava.nativelib.NativeLoader.loadLibrary("secp256k1")

  val ls1 = org.bitcoins.crypto.ECPrivateKey(
    "1111111111111111111111111111111111111111111111111111111111111111"
  )

  val ls2 = org.bitcoins.crypto.ECPrivateKey(
    "2121212121212121212121212121212121212121212121212121212121212121"
  )

  val rs = org.bitcoins.crypto.ECPublicKey(
    "028d7500dd4c12685d1f568b4c2b5048e8534b873319f3a8daa612b469132ec7f7"
  )

  import noise.*
  import perun.crypto.keygen.*
  import perun.crypto.secp256k1.*
  import util.*

  import perun.db.*

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

  def run(args: List[String]): URIO[ZEnv, ExitCode] =
    ZStream
      .fromSocketServer(9977, None)
      .foreach { c =>
        handshake(responder, c.read, c.write)
          .flatMap { (peer, leftover) =>
            perun.peer
              .start(
                Stream.fromIterable(leftover.toArray) ++ c.read,
                c.write,
                c.close(),
                peer.rk,
                peer.sk
              )
              .forkDaemon
          }
      }
      .onInterrupt(ZIO.effectTotal(println("DOOONE")))
      .provideCustomLayer(
        perun.crypto.keygen.live ++
          Store.live("jdbc:hsqldb:file:testdb") ++
          tinkerpop.inMemory ++ native
      )
      .exitCode

end lnz
