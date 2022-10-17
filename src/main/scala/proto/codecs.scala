package perun.proto.codecs

import java.net.{Inet4Address, Inet6Address, InetAddress}

import scala.annotation.targetName

import org.bitcoins.crypto.{ECDigitalSignature, ECPrivateKey, ECPublicKey}
import scodec.*
import scodec.bits.*
import scodec.codecs.*
import zio.UIO

import perun.crypto.*
import perun.proto.*

export perun.crypto.Signature.codec as signature

opaque type ChannelId = ByteVector

case class AllChannels()

object ChannelId:
  def make(bytes: ByteVector): Either[String, ChannelId] =
    if bytes.length == 32 then Right(bytes) else Left("must be 32")

extension (c: ChannelId)
  @targetName("channelIdToBytes")
  def toBytes: ByteVector = c

val channelId: Codec[ChannelId] = bytes(32)

case class Error(channel: ChannelId | AllChannels, data: String)

val allChannels: ByteVector = ByteVector.fill(32)(0)

val error: Codec[Error] =
  (channelId :: variableSizeBytes(uint16, ascii))
    .xmap(
      {
        case (c, s) if c == allChannels =>
          Error(AllChannels(), s)
        case (c, s) => Error(c, s)
      },
      {
        case Error(c: ChannelId, d)  => (c, d)
        case Error(AllChannels(), d) => (allChannels, d)
      }
    )

export perun.p2p.Alias.codec as alias
export perun.p2p.NodeId.codec as nodeId
export perun.p2p.Point.codec as point
export perun.p2p.ShortChannelId.codec as shortChannelId
export perun.p2p.ShortChannelId.codecEncoded as encodedShortIds
export perun.p2p.Timestamp.codec as timestamp

enum Address:
  case Ip(ip: InetAddress, port: Int)
  case Tor2(addr: ByteVector, port: Int)
  case Tor3(addr: ByteVector, port: Int)

val ipv4: Codec[InetAddress] = bytes(4).xmap(
  v => InetAddress.getByAddress(v.toArray),
  a => ByteVector.view(a.getAddress)
)

val ipv6: Codec[InetAddress] = bytes(16).xmap(
  v => InetAddress.getByAddress(v.toArray),
  a => ByteVector.view(a.getAddress)
)

val address: Codec[Address] =
  discriminated[Address]
    .by(uint8)
    .subcaseP(1) { case i @ Address.Ip(_: Inet4Address, _) => i }(
      (ipv4 :: uint16).as[Address.Ip]
    )
    .subcaseP(2) { case i @ Address.Ip(_: Inet6Address, _) => i }(
      (ipv6 :: uint16).as[Address.Ip]
    )
    .subcaseP(3) { case t: Address.Tor2 => t }(
      (bytes(10) :: uint16).as[Address.Tor2]
    )
    .subcaseP(4) { case t: Address.Tor3 => t }(
      (bytes(35) :: uint16).as[Address.Tor3]
    )

case class Color(r: Byte, g: Byte, b: Byte):
  def hex: String = f"$r%02X$g%02X$b%02X"
  override def toString(): String = f"#$r%02X$g%02X$b%02X"

val color: Codec[Color] = (byte :: byte :: byte).as[Color]

opaque type Msat = BigInt
val msat: Codec[Msat] = uint64.uint64

extension (m: Msat) def msatToBtc: BigDecimal = BigDecimal(m) / 1e11

opaque type Sat = BigInt
val sat: Codec[Sat] = uint64.uint64

// export UInt64.uint64
// export UInt64.bigsize
// export perun.proto.init.codec as init
// export perun.proto.types.features.codec as features
