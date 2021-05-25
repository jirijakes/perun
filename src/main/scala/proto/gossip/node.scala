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
import perun.proto.signed.*
import perun.proto.uint64.*

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
  signed[NodeAnnouncement]
    .withKey(_.nodeId.publicKey)(
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
    )

val xxx: Codec[NodeAnnouncement] = proto[NodeAnnouncement]

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

// val nodeAnnouncement2: Codec[NodeAnnouncement] =
//   signet[NodeAnnouncement](64)(a => (a.signature, a.nodeId.publicKey))
//   ((
//     ("signature" | signature) ::
//       ("features" | features) ::
//       ("timestamp" | uint32) ::
//       ("node_id" | nodeId) ::
//       ("rgb_color" | color) ::
//       ("alias" | alias) ::
//       ("addresses" | vector(
//         variableSizeBytes("addrlen" | uint16, address)
//       )) ::
//       ("unknown" | bytes)
//   ).as[NodeAnnouncement])

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
    bitcoinKey2: NodeId
)

val channelAnnouncement: Codec[ChannelAnnouncement] = proto

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
