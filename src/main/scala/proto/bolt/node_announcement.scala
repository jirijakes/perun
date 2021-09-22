package perun.proto.bolt.nodeAnnouncement

import org.bitcoins.crypto.CryptoUtil.doubleSHA256
import org.typelevel.paiges.Doc.*
import scodec.bits.ByteVector
import zio.*
import zio.prelude.*

import perun.crypto.secp256k1.*
import perun.proto.bolt.bolt.*
import perun.proto.bolt.doc.*
import perun.proto.gossip.NodeAnnouncement
import perun.db.p2p.*

val validateSignature: Val[Has[Secp256k1], Nothing, NodeAnnouncement] =
  validate(
    ctx =>
      predicateM(
        verifySignature(
          ctx.message.signature,
          doubleSHA256(ctx.bytes.drop(2 + 64)),
          ctx.message.nodeId
        )
      )(_ == true, ctx.message, failConnection("Signature is invalid.")),
    text("if") & field("signature") & split(
      "is NOT a valid signature (using"
    ) & field("node_id") & split(
      "of the double-SHA256 of the entire message following the"
    ) & field("signature") & split(
      "field, including any future fields appended to the end)"
    ) + char(':') /
      (text("SHOULD fail the connection.") /
        text("MUST NOT process the message further.")).indent(3)
  )

val validatePreviousChannel: Val[Has[P2P], Throwable, NodeAnnouncement] =
  validate(
    ctx =>
      val chan =
        predicateM(findChannels(ctx.message.nodeId))(
          _.nonEmpty,
          ctx.message,
          ignore("No known channel for node ID found, required at least one.")
        )
      val node =
        predicateM(findNode(ctx.message.nodeId))(
          _.exists(_.timestamp < ctx.message.timestamp),
          ctx.message,
          ignore("No known node for node ID found, required at least one.")
        )

      chan.zipWithPar(node)(_ &> _),
    split("if") & field("node_id") & split(
      "is NOT previously known from a"
    ) & field("channel_announcement") & split("message, OR if") & field(
      "timestamp"
    ) & split("is NOT greater than the last-received") & field(
      "node_announcement"
    ) & split("from this") & field("node_id") + char(':') / text(
      "SHOULD ignore the message."
    ).indent(3)
  )

val validation: Bolt[Has[P2P] & Has[Secp256k1], Throwable, NodeAnnouncement] =
  bolt("#7", "Node announcement")(
    validateSignature,
    validatePreviousChannel
  )
