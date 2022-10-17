package perun.net.rpc

import io.circe.syntax.*
import io.circe.{Decoder, Encoder, Json}
import sttp.client3.*
import sttp.client3.circe.*
import sttp.client3.httpclient.zio.*
import sttp.model.Uri
import zio.*

export sttp.client3.UriContext
import sttp.client3.httpclient.zio.HttpClientZioBackend

import perun.p2p.*

final case class Response[A](result: Option[A]) derives Decoder

final case class ScriptPubKey(hex: String) derives Decoder

final case class TxOut(scriptPubKey: ScriptPubKey, value: BigDecimal)
    derives Decoder

final case class Tx(txid: String, vout: Vector[TxOut]) derives Decoder

final case class Block(tx: Vector[Tx]) derives Decoder

trait Rpc:
  def txout(block: Int, tx: Int, out: Int): Task[Option[TxOut]]

case class BitcoinDRpc(
    endpoint: Uri,
    user: String,
    password: String,
    cl: SttpBackend[Task, Any],
    sem: Semaphore
) extends Rpc:

  class RpcPartialApply[Res]:
    def apply[P <: Tuple: Encoder](
        method: String
    )(params: P = EmptyTuple)(using Decoder[Res]): Task[Option[Res]] =
      sem
        .withPermit {
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
        }
        .map(_.body)
        .absolve
        .map(_.result)

  def rpc[Res] = new RpcPartialApply[Res]
  def txout(block: Int, tx: Int, out: Int): Task[Option[TxOut]] =
    for
      h <- rpc[String]("getblockhash")(Tuple1(block))
      b <- rpc[Block]("getblock")(h.get, 2)
      o <- rpc[TxOut]("gettxout")(b.get.tx(tx).txid, out)
    yield o

def bitcoind(
    endpoint: Uri,
    user: String,
    password: String
): ZLayer[Any, Throwable, Rpc] =
  ZLayer {
    for
      cl <- HttpClientZioBackend()
      sem <- Semaphore.make(2)
    yield BitcoinDRpc(endpoint, user, password, cl, sem)
  }

def txout(
    block: Int,
    tx: Int,
    out: Int
): ZIO[Rpc, Throwable, Option[TxOut]] =
  ZIO.serviceWithZIO[Rpc](_.txout(block, tx, out))

def txout(
    shortChannelId: ShortChannelId
): ZIO[Rpc, Throwable, Option[TxOut]] =
  txout(shortChannelId.block, shortChannelId.transaction, shortChannelId.output)
