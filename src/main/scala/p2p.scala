package perun.p2p

import scodec.Codec
import scodec.bits.ByteVector
import scodec.codecs.*

import scala.util.Try

import perun.crypto.*

import zio.prelude.Ord

opaque type Timestamp = Long

object Timestamp:
  given Ord[Timestamp] = zio.prelude.Equal.LongHashOrd
  def fromTimestamp(l: Long): Timestamp = l
  val codec: Codec[Timestamp] = uint32

final case class Node(
    nodeId: NodeId,
    timestamp: Timestamp,
    blacklisted: Boolean
)
final case class Channel(
    shortChannelId: ShortChannelId,
    node1: NodeId,
    node2: NodeId
)

opaque type NodeId = PublicKey

extension (id: NodeId)
  def nodeIdAsPublicKey: PublicKey = id
  def hex: String = id.bytes.toHex

object NodeId:
  def fromPublicKey(pub: PublicKey): NodeId = pub
  def fromHex(hex: String): NodeId = PublicKey.fromHex(hex)
  val codec: Codec[NodeId] = perun.crypto.PublicKey.codec

case class ShortChannelId(block: Int, transaction: Int, output: Int):
  override def toString = s"${block}x${transaction}x${output}"
// import fansi.Color.*
// s"${Cyan(block.toString)}${DarkGray("x")}${Yellow(
// transaction.toString
// )}${DarkGray("x")}${LightGreen(output.toString)}"

object ShortChannelId:
  def fromString(s: String): Option[ShortChannelId] =
    s.split("x") match
      case Array(bl, tx, out) =>
        Try(ShortChannelId(bl.toInt, tx.toInt, out.toInt)).toOption
      case _ => None

  given Ordering[ShortChannelId] =
    Ordering.by(s => (s.block, s.transaction, s.output))

  val codec: Codec[ShortChannelId] =
    (
      ("block_height" | uint(24)) ::
        ("transaction" | uint(24)) ::
        ("output" | uint(16))
    ).as[ShortChannelId]

  val codecEncoded: Codec[Vector[ShortChannelId]] =
    discriminated
      .by(uint8)
      .typecase(0, vector(codec).xmap(identity, _.sorted))
      .typecase(1, zlib(vector(codec)).xmap(identity, _.sorted))

opaque type Alias = ByteVector

extension (a: Alias)
  def asText: Option[String] =
    a.decodeUtf8.toOption.map(_.trim)

object Alias:
  def apply(s: String) = ByteVector.view(s.getBytes)
  def apply(b: ByteVector) = b
  val codec: Codec[Alias] = bytes(32)
