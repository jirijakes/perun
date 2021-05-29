import zio.*
import zio.console.*
import zio.stream.*

import org.zeromq.*

object pokus extends App:

  // def main(args: Array[String]): Unit =
  //   val z = MultiSignatureScriptPubKey(
  //     2,
  //     List(
  //       ECPublicKey.fromHex(
  //         "032ddc3d892921ffc611a8e0e1aaa31862186f1888b7d62804b88e35af60e57dba"
  //       ),
  //       ECPublicKey.fromHex(
  //         "03a3b22e0c0616fd16df98e5b40d7eb614137cb494241690149be225f0cefb80bc"
  //       )
  //     )
  //   )
  //   println(">>> " + CryptoUtil.sha256(z.asmBytes))

  def zmq: Layer[Throwable, Has[ZMQ.Socket]] =
    ZManaged
      .fromAutoCloseable(UIO(new ZContext()))
      .mapM(ctx => Task(ctx.createSocket(SocketType.SUB)))
      .tapM(s => Task(s.connect("tcp://localhost:28332")))
      .tapM(s => Task(s.subscribe("hash")))
      .toLayer

  val program: ZIO[Has[ZMQ.Socket] with Console, Throwable, Unit] =
    ZIO
      .environment[Has[ZMQ.Socket]]
      .map(_.get)
      .flatMap(s =>
        for
          t <- Task(s.recvStr(ZMQ.DONTWAIT))
          _ <- if t == null then ZIO.unit else putStrLn(t)
          h <- Task(s.recv(ZMQ.DONTWAIT))
          _ <-
            if h == null then ZIO.unit
            else putStrLn(scodec.bits.ByteVector.view(h).toString)
          x <- Task(s.recvStr(ZMQ.DONTWAIT))
          _ <- if x == null then ZIO.unit else putStrLn(x)
        yield ()
      )
      .forever

  def run(args: List[String]) = program.provideCustomLayer(zmq).exitCode
