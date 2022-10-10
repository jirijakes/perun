package perun.proto.gossip

import java.time.LocalDate

import com.softwaremill.quicklens.*
import org.bitcoins.crypto.CryptoUtil.sha256
import org.typelevel.paiges.*
import scodec.Codec
import scodec.bits.ByteVector
import scodec.codecs.*
import zio.*

import perun.crypto.*
import perun.crypto.secp256k1.*
import perun.p2p.*
import perun.proto.blockchain.*
import perun.proto.codecs.*
import perun.proto.features.*
import perun.proto.uint64.*
import perun.proto.validate.*

final case class NodeAnnouncement(
    signature: Signature,
    features: Features,
    timestamp: Timestamp,
    nodeId: NodeId,
    color: Color,
    alias: Alias,
    addresses: Vector[Address],
    unknown: ByteVector
)

val nodeAnnouncement: Codec[NodeAnnouncement] =
  validated[NodeAnnouncement](
    (
      ("signature" | signature) ::
        ("features" | features) ::
        ("timestamp" | timestamp) ::
        ("node_id" | nodeId) ::
        ("rgb_color" | color) ::
        ("alias" | alias) ::
        ("addresses" | vector(
          variableSizeBytes("addrlen" | uint16, address)
        )) ::
        ("unknown" | bytes)
    ).as[NodeAnnouncement]
  )(signed(64, _.signature, _.nodeId))

object NodeAnnouncement:
  given Document[NodeAnnouncement] with
    def document(n: NodeAnnouncement) =
      import Doc.*
      import Style.Ansi.*
      text("Node Announcement").style(Fg.BrightYellow ++ Attr.Bold) /
        (
          tabulate(
            ' ',
            ": ",
            List(
              ("id", text(n.nodeId.hex)),
              ("color", text(n.color.toString)),
              ("address", text(n.addresses.mkString(", ")))
            )
          )
        ).indent(3)

final case class ChannelAnnouncement(
    nodeSignature1: Signature,
    nodeSignature2: Signature,
    bitcoinSignature1: Signature,
    bitcoinSignature2: Signature,
    features: Features,
    chain: Chain,
    shortChannelId: ShortChannelId,
    nodeId1: NodeId,
    nodeId2: NodeId,
    bitcoinKey1: NodeId,
    bitcoinKey2: NodeId,
    unknown: ByteVector
):

  /** Verify validity of signatures.
    */
  def isValid: URIO[Secp256k1, Boolean] =
    ZIO
      .succeed(
        nonvalidatingChannelAnnouncement
          .encode(this)
          .toOption
          .map(_.drop(2048).toByteVector)
      )
      .flatMap {
        case None => ZIO.succeed(false)
        case Some(msg) =>
          val hash = sha256(msg)
          ZIO
            .collectAll(
              NonEmptyChunk(
                verifySignature(
                  this.nodeSignature1,
                  hash,
                  this.nodeId1
                ),
                verifySignature(
                  this.nodeSignature2,
                  hash,
                  this.nodeId2
                ),
                verifySignature(
                  this.bitcoinSignature1,
                  hash,
                  this.bitcoinKey1
                ),
                verifySignature(
                  this.bitcoinSignature2,
                  hash,
                  this.bitcoinKey2
                )
              )
            )
            .map(_.forall(_ == true))
      }

  /** Provide signature of this channel announcement using provided
    * secret key. Signing may fail, in that case `None` is returned.
    *
    * @param sec secret key used for signature
    * @return `None` if signing failed, otherwise `Some`
    */
  def signature(
      sec: PrivateKey
  ): ZIO[Secp256k1, Option[Nothing], Signature] =
    ZIO
      .succeed(
        nonvalidatingChannelAnnouncement
          .encode(this)
          .toOption
          .map(_.drop(2048).toByteVector)
      )
      .flatMap {
        case None      => ZIO.none
        case Some(msg) => signMessage(sec, sha256(msg)).map(Option(_))
      }
      .some

  /** Inject all signatures into this channel announcement. The
    * signatures are not verified during this step.
    *
    * @return channel announcement with all signatures replaced with arguments
    */
  def signed(
      nodeSignature1: Signature,
      nodeSignature2: Signature,
      bitcoinSignature1: Signature,
      bitcoinSignature2: Signature
  ): ChannelAnnouncement =
    this
      .modify(_.nodeSignature1)
      .setTo(nodeSignature1)
      .modify(_.nodeSignature2)
      .setTo(nodeSignature2)
      .modify(_.bitcoinSignature1)
      .setTo(bitcoinSignature1)
      .modify(_.bitcoinSignature2)
      .setTo(bitcoinSignature2)

private[gossip] val nonvalidatingChannelAnnouncement
    : Codec[ChannelAnnouncement] =
  (
    ("node_signature_1" | signature) ::
      ("node_signature_2" | signature) ::
      ("bitcoin_signature_1" | signature) ::
      ("bitcoin_signature_2" | signature) ::
      ("features" | features) ::
      ("chain_hash" | chain) ::
      ("short_channel_id" | shortChannelId) ::
      ("node_id_1" | nodeId) ::
      ("node_id_2" | nodeId) ::
      ("bitcoin_key_1" | nodeId) ::
      ("bitcoin_key_2" | nodeId) ::
      ("unknown_bytes" | bytes)
  ).as[ChannelAnnouncement]

val channelAnnouncement: Codec[ChannelAnnouncement] =
  validated(nonvalidatingChannelAnnouncement)(
    signed(
      2048,
      _.nodeSignature1,
      _.nodeId1,
      _.nodeSignature2,
      _.nodeId2
    )
  )

// val channelAnnouncement: Codec[ChannelAnnouncement] = proto

final case class ChannelUpdate(
    signature: Signature,
    chain: Chain,
    shortChannelId: ShortChannelId,
    timestamp: Long,
    messageFlags: Byte,
    channelFlags: Byte,
    cltvExpiryDelta: Int,
    htlcMinimumMsat: Msat,
    feeBaseMsat: Long,
    feeProportionalMillionths: Long,
    htlcMaximumMsat: Msat
)

val channelUpdate: Codec[ChannelUpdate] =
  (
    ("signature" | signature) ::
      ("chain_hash" | chain) ::
      ("short_channel_id" | shortChannelId) ::
      ("timestamp" | uint32) ::
      ("message_flags" | byte) ::
      ("channel_flags" | byte) ::
      ("cltv_expiry_delta" | uint16) ::
      ("htlc_minimum_msat" | msat) ::
      ("fee_base_msat" | uint32) ::
      ("fee_proportional_millionths" | uint32) ::
      ("htlc_maximum_msat" | msat)
  ).as[ChannelUpdate]
