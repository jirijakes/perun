import sttp.client3.httpclient.zio.HttpClientZioBackend
import zhttp.http.*
import zhttp.service.*
import zio.*
import zio.console.*
import zio.stream.*

import perun.crypto.keygen.*
import perun.crypto.secp256k1.*
import perun.db.p2p.*
import perun.db.tinkerpop
import perun.net.rpc.*
import perun.net.zmq.*

object pokus extends App:

  val prg =
    for
      res <- Client.request(
        Request(
          (Method.POST, URL.fromString("").getOrElse(???)),
          content = HttpData.CompleteData(???)
        )
      )
      // _ = res.
    yield ()

  def run(args: List[String]) = ???

object pokusHas extends App:

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

  object A:
    class Service
  object B:
    class Service
  object C:
    class Service
  object D:
    class Service
  object E:
    class Service

  val a = ZLayer.succeed(new A.Service)
  val b = ZLayer.succeed(new B.Service)
  val c = ZLayer.succeed(new C.Service)
  val d = ZLayer.succeed(new D.Service)
  val e = ZLayer.succeed(new E.Service)

  type A = Has[A.Service]
  type B = Has[B.Service]
  type C = Has[C.Service]
  type D = Has[D.Service]
  type E = Has[E.Service]

  val prg: ZIO[A & B & C & D & E, Nothing, Unit] = ZIO.unit

  val program: ZIO[Has[
    P2P
  ] & Has[Keygen] & Has[Secp256k1] & Has[Zmq] & Has[Rpc], Throwable, Unit] =
    ZIO.unit
  // txout(1000, 0, 0).flatMap(x => putStrLn(x.toString)) *>
  // subscribeZmq.mapM(x => putStrLn(x.toString)).runDrain

  def run(args: List[String]) =
    prg.provideCustomLayer(a ++ b ++ c ++ d ++ e).exitCode
