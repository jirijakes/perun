package perun.proto.bolt.channelUpdate

import org.bitcoins.crypto.CryptoUtil.{doubleSHA256, sha256}
import org.typelevel.paiges.Doc.*
import scodec.bits.ByteVector
import zio.*
import zio.prelude.*

import perun.proto.bolt.bolt.*
import perun.proto.bolt.doc.*
import perun.proto.gossip.ChannelUpdate
import perun.db.p2p.*
import perun.net.rpc.*
import perun.proto.codecs.*
import perun.crypto.secp256k1.*

val validateSignature: Val[P2P & Secp256k1, Throwable, ChannelUpdate] =
  validate(
    ctx =>
      val hash = doubleSHA256(ctx.bytes.drop(2 + 64))
      findChannel(ctx.message.shortChannelId).flatMap {
        case None =>
          ZIO.succeed(Validation.fail(failConnection("Signature is invalid.")))
        case Some(ch) =>
          val nodeId =
            if ctx.message.channelFlags.originator == ChannelUpdate.Originator.Node1 then
              ch.node1
            else ch.node2
          predicateM(verifySignature(ctx.message.signature, hash, nodeId))(
            identity,
            ctx.message,
            failConnection("Signature is invalid.")
          )

      },
    text("if") & field("signature") & split(
      "is not a valid signature, using"
    ) & field("node_id") & split(
      "of the double-SHA256 of the entire message following the"
    ) & field("signature") & split(
      "field (including unknown fields following"
    ) & field("fee_proportional_millionths") & char(')') & char(':') /
      (split("SHOULD send a") & field("warning") & split(
        "and close the connection."
      )).indent(3) /
      split("MUST NOT process the message further.").indent(3)
  )

val validateTimestamp: Val[P2P, Throwable, ChannelUpdate] =
  validate(
    ctx =>
      findChannel(ctx.message.shortChannelId)
        .flatMap(ch =>
          expect(
            ch.exists(_.timestamp.forall(_ <= ctx.message.timestamp)),
            ctx.message,
            ignore("Timestamp is lower than previous one.")
          )
        ),
    text("if") & field("timestamp") & split(
      "is lower than that of the last-received"
    ) & field("channel_update") & split("for this") & field(
      "short_channel_id"
    ) & split("AND for") & field("node_id") & char(':') / split(
      "SHOULD ignore the message."
    ).indent(3)
  )

val validateCapacity: Val[Rpc, Throwable, ChannelUpdate] =
  validate(
    ctx =>
      predicateM(txout(ctx.message.shortChannelId))(
        _.exists { tx =>
          ctx.message.htlcMaximumMsat.msatToBtc <= tx.value
        },
        ctx.message,
        ignore("htlc_maximum_msat is greater than channel capacity.")
      ),
    text("if") & field("htlc_maximum_msat") &
      split("is greater than channel capacity") & char(':') /
      (split("MAY blacklist this") & field("node_id")).indent(3) /
      split("SHOULD ignore this channel during route considerations.").indent(3)
  )

val validatePreviousChannel: Val[P2P, Throwable, ChannelUpdate] =
  validate(
    ctx =>
      predicateM(findChannel(ctx.message.shortChannelId))(
        _.nonEmpty,
        ctx.message,
        ignore("Channel is unknown.")
      ),
    split("if the") & field("short_channel_id") & split(
      "does NOT match a previous"
    ) & field("channel_announcement") + split(
      ", OR if the channel has been closed in the meantime"
    ) + char(':') / (split("MUST ignore") & field("channel_update") + split(
      "'s that do NOT correspond to one of its own channels."
    )).indent(3)
  )

val validation: Bolt[P2P & Rpc & Secp256k1, Throwable, ChannelUpdate] =
  bolt("#7", "Channel update")(
    validateSignature,
    validatePreviousChannel,
    validateTimestamp,
    validateCapacity
  )
