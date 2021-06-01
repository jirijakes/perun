package perun.proto.gossip

import java.time.LocalDate

import org.hsqldb.index.NodeAVLDisk
import org.typelevel.paiges.*
import scodec.Codec
import scodec.bits.ByteVector
import scodec.codecs.*

import perun.proto.blockchain.*
import perun.proto.codecs.*
import perun.proto.features.*
import perun.proto.generic.proto
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
  )(signed(64, _.signature, _.nodeId.publicKey))

// val xxx: Codec[NodeAnnouncement] = proto[NodeAnnouncement]

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
) //:
//def witness: ByteVector = channelAnnouncement.encode(this).map(_.drop(2048))

val channelAnnouncement: Codec[ChannelAnnouncement] =
  validated[ChannelAnnouncement](
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
  )(
    signed(
      2048,
      _.nodeSignature1,
      _.nodeId1.publicKey,
      _.nodeSignature2,
      _.nodeId2.publicKey
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
      ("hltc_maximum_msat" | msat)
  ).as[ChannelUpdate]
