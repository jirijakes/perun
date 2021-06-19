package perun.proto

import scodec.Codec
import scodec.bits.*
import scodec.codecs.*

import perun.p2p.*

enum Message:
  case Init(m: init.Init)
  case Ping(m: ping.Ping)
  case Pong(m: ping.Pong)
  case QueryChanellRange(m: gossip.QueryChannelRange)
  case ReplyChannelRange(m: gossip.ReplyChannelRange)
  case GossipTimestampFilter(m: gossip.GossipTimestampFilter)
  case NodeAnnouncement(m: gossip.NodeAnnouncement)
  case ChannelAnnouncement(
      m: gossip.ChannelAnnouncement,
      spk: Option[String],
      channel: Option[Channel]
  )
  case ChannelUpdate(m: gossip.ChannelUpdate, channel: Option[Channel])

import Message.*

// format: off
val messageCodec: Codec[Message] = discriminated[Message].by(uint16)
  .caseP(16)  { case Init(m)                      => m }(Init.apply                              )(init.init                   )
  .caseP(18)  { case Ping(m)                      => m }(Ping.apply                              )(ping.ping                   )
  .caseP(19)  { case Pong(m)                      => m }(Pong.apply                              )(ping.pong                   )
  .caseP(256) { case ChannelAnnouncement(m, _, _) => m }(ChannelAnnouncement.apply(_, None, None))(gossip.channelAnnouncement  )
  .caseP(257) { case NodeAnnouncement(m)          => m }(NodeAnnouncement.apply                  )(gossip.nodeAnnouncement     )
  .caseP(258) { case ChannelUpdate(m, _)          => m }(ChannelUpdate.apply(_, None)            )(gossip.channelUpdate        )
  .caseP(263) { case QueryChanellRange(m)         => m }(QueryChanellRange.apply                 )(gossip.queryChannelRange    )
  .caseP(264) { case ReplyChannelRange(m)         => m }(ReplyChannelRange.apply                 )(gossip.replyChannelRange    )
  .caseP(265) { case GossipTimestampFilter(m)     => m }(GossipTimestampFilter.apply             )(gossip.gossipTimestampFilter)
// format: on

enum Response:
  case Send(m: Message)
  case Ignore
  case FailConnection

def decode(b: ByteVector): Either[String, Message] =
  messageCodec.decodeValue(b.toBitVector).toEither.left.map(_.toString)

def encode(m: Message): ByteVector =
  messageCodec.encode(m).getOrElse(???).toByteVector
