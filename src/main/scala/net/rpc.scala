package perun.net.rpc

import scala.language.experimental.namedTypeArguments

import io.circe.{Decoder, Encoder, Json}
import io.circe.syntax.*
import sttp.client3.*
import sttp.client3.circe.*
import sttp.client3.httpclient.zio.*
import sttp.model.Uri
import zio.*

export sttp.client3.UriContext
export sttp.client3.httpclient.zio.HttpClientZioBackend

final case class Response[A](result: A) derives Decoder

final case class ScriptPubKey(hex: String) derives Decoder

final case class Vout(scriptPubKey: ScriptPubKey) derives Decoder

final case class Tx(vout: Vector[Vout]) derives Decoder

final case class Block(tx: Vector[Tx]) derives Decoder

trait Rpc:
  def txout(block: Int, tx: Int, out: Int): Task[Vout]

def bitcoind(
    endpoint: Uri,
    user: String,
    password: String
): ZLayer[SttpClient, Nothing, Has[Rpc]] =
  ZLayer.fromService { cl =>
    def call[Res: Decoder, P <: Tuple : Encoder](method: String)(params: P = EmptyTuple): Task[Res] =
      quickRequest
        .post(endpoint)
        .auth
        .basic(user, password)
        .body(
          Json.obj(
            "jsonrpc" := "2.0",
            "method" := method,
            "params" := params
          )
        )
        .response(asJson[Response[Res]])
        .send(cl)
        .map(_.body)
        .absolve
        .map(_.result)

    new Rpc:
      def txout(block: Int, tx: Int, out: Int): Task[Vout] =
        for
          h <- call[Res = String]("getblockhash")(Tuple1(block))
          b <- call[Res = Block]("getblock")(h, 2)
        yield b.tx(tx).vout(out)

  }

def txout(block: Int, tx: Int, out: Int): ZIO[Has[Rpc], Throwable, Vout] =
  ZIO.accessM(_.get.txout(block, tx, out))
