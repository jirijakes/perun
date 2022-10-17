package perun.proto.bolt.channelUpdate

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

val validateTimestamp: Val[P2P, Throwable, ChannelUpdate] =
  validate(
    ctx =>
      predicateM(findChannel(ctx.message.shortChannelId))(
        _.forall(_.timestamp.exists(_ < ctx.message.timestamp)),
        ctx.message,
        ignore("Timestamp is lower than previous one.")
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

val validation: Bolt[P2P & Rpc, Throwable, ChannelUpdate] =
  bolt("#7", "Channel update")(
    validatePreviousChannel,
    validateTimestamp,
    validateCapacity
  )
