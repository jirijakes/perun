import scodec.bits.*

import zio.*
import zio.console.*
import zio.logging.*
import zio.stream.*

object lnz extends App:

  val x = org.bitcoins.crypto.ECPublicKey.dummy

// val y = ChannelId
//   .make(
//     ByteVector.fromValidHex(
//       "0x000000000000000000000000000000000000000000000000000000000000"
//     )
//   )
//   .getOrElse(???)

// val e = Error(y, "boom")

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
  import crypto.keygen.*
  import util.*

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

  // val init: Either[Peer, HandshakeState] = Right(responder)

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

  def run2(args: List[String]) = ZStream
    .iterate(1)(_ + 1)
    .take(100)
    .chunkN(3)
    .transduce(collectByLengths(11, 8, 4))
    .foreach(x => putStrLn(x.toString))
    .exitCode

  def run(args: List[String]): URIO[ZEnv, ExitCode] =
    ZStream
      .fromSocketServer(9977, None)
      // .mapM
      .mapMParUnordered(10) { c =>
        handshake(responder, c.read, c.write).flatMap { (peer, leftover) =>
          val str = Stream.fromIterable(leftover.toArray) ++ c.read
          str
            .transduce(perun.peer.decrypt(peer.rk))
            .foreach(x => putStrLn(x.toString))
        } *> c.close()
      // c.read
      // .transduce(ZTransducer.utf8Decode)
      // .flatMap(s => ZStream.fromIterable(s.reverse.getBytes))
      // .map(Chunk.fromArray)
      // .run(c.write)
      // .foreach(x => putStrLn(x))
      // .as(c)
      }
      .tap(x => ZIO.effectTotal(println("CLOSED: " + x)))
      .forever
      .runDrain
      // .flatMap(_ => ZIO.never)
      .provideCustomLayer(crypto.keygen.live)
      // .provideLayer(Logging.console())
      .exitCode

end lnz
