package perun.proto

import scodec.Codec
import scodec.bits.*
import scodec.codecs.*

// export init.Init
// export types.features.Features

enum Message:
  case Init(m: init.Init)
  case Ping(m: ping.Ping)
  case Pong(m: ping.Pong)
  case QueryChanellRange(m: gossip.QueryChannelRange)
  case ReplyChannelRange(m: gossip.ReplyChannelRange)
  case GossipTimestampFilter(m: gossip.GossipTimestampFilter)
  case NodeAnnouncement(m: gossip.NodeAnnouncement)
  case ChannelAnnouncement(m: gossip.ChannelAnnouncement)
  case ChannelUpdate(m: gossip.ChannelUpdate)

import Message.*

// format: off
val messageCodec: Codec[Message] = discriminated[Message].by(uint16)
  .caseP(16)  { case Init(m)                  => m }(Init.apply                 )(init.init                   )
  .caseP(18)  { case Ping(m)                  => m }(Ping.apply                 )(ping.ping                   )
  .caseP(19)  { case Pong(m)                  => m }(Pong.apply                 )(ping.pong                   )
  .caseP(256) { case ChannelAnnouncement(m)   => m }(ChannelAnnouncement.apply  )(gossip.channelAnnouncement  )
  .caseP(257) { case NodeAnnouncement(m)      => m }(NodeAnnouncement.apply     )(gossip.nodeAnnouncement     )
  .caseP(258) { case ChannelUpdate(m)         => m }(ChannelUpdate.apply        )(gossip.channelUpdate        )
  .caseP(263) { case QueryChanellRange(m)     => m }(QueryChanellRange.apply    )(gossip.queryChannelRange    )
  .caseP(264) { case ReplyChannelRange(m)     => m }(ReplyChannelRange.apply    )(gossip.replyChannelRange    )
  .caseP(265) { case GossipTimestampFilter(m) => m }(GossipTimestampFilter.apply)(gossip.gossipTimestampFilter)
// format: on

enum Response:
  case Send(m: Message)
  case Ignore

def decode(b: ByteVector): Either[String, Message] =
  messageCodec.decodeValue(b.toBitVector).toEither.left.map(_.toString)

def encode(m: Message): ByteVector =
  messageCodec.encode(m).getOrElse(???).toByteVector
