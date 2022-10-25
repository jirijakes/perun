package perun.net.rpc

import sttp.client3.*
import sttp.client3.ziojson.*
import sttp.client3.httpclient.zio.*
import sttp.model.Uri
import zio.*
import zio.json.*

export sttp.client3.UriContext
import sttp.client3.httpclient.zio.HttpClientZioBackend

import perun.p2p.*

final case class Response[A](result: Option[A])
final case class ScriptPubKey(hex: String)
final case class TxOut(scriptPubKey: ScriptPubKey, value: BigDecimal)
final case class Tx(txid: String, vout: Vector[TxOut])
final case class Block(tx: Vector[Tx])
final case class Request[T](jsonrpc: String, method: String, params: T)

given [A](using JsonDecoder[A]): JsonDecoder[Response[A]] =
  DeriveJsonDecoder.gen
given JsonDecoder[ScriptPubKey] = DeriveJsonDecoder.gen
given JsonDecoder[TxOut] = DeriveJsonDecoder.gen
given JsonDecoder[Tx] = DeriveJsonDecoder.gen
given JsonDecoder[Block] = DeriveJsonDecoder.gen
given [T](using JsonEncoder[T]): JsonEncoder[Request[T]] = DeriveJsonEncoder.gen

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
    def apply[P <: Tuple: JsonEncoder](
        method: String
    )(params: P = EmptyTuple)(using JsonDecoder[Res]): Task[Option[Res]] =
      sem
        .withPermit {
          quickRequest
            .post(endpoint)
            .auth
            .basic(user, password)
            .body(Request("2.0", method, params))
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
