import zio.*
import zio.console.*
import zio.stream.*

import perun.net.jsonrpc.service.*
import sttp.client3.*
import sttp.client3.httpclient.zio.*
import sttp.model.*
import sttp.client3.SttpBackend

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

  def run(args: List[String]) =
    blockchainInfo
      .flatMap(i => putStrLn(i.toString))
      .provideCustomLayer(
        HttpClientZioBackend.layer() >>> live(
          uri"http://@10.0.0.21:18332"
        )
      )
      .exitCode
