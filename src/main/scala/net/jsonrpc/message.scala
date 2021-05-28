package perun.net.jsonrpc.message

import io.circe.Decoder

final case class Response[A](result: A)

final case class BlockchainInfo(chain: String, blocks: Int)

object BlockchainInfo:
  given Decoder[BlockchainInfo] =
    Decoder.forProduct2("chain", "blocks")(BlockchainInfo.apply)

object Response:
  given [A: Decoder]: Decoder[Response[A]] =
    Decoder.instance(c => c.get[A]("result").map(Response.apply))
