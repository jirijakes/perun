package perun.proto.bolt.channelUpdate

import org.typelevel.paiges.Doc.*
import scodec.bits.ByteVector
import zio.*
import zio.prelude.*

import perun.proto.bolt.bolt.*
import perun.proto.bolt.doc.*
import perun.proto.gossip.ChannelUpdate
import perun.db.p2p.*

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

val validation: Bolt[P2P, Throwable, ChannelUpdate] =
  bolt("#7", "Channel update")(
    validatePreviousChannel
  )
