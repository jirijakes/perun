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

val messageCodec: Codec[Message] =
  discriminated[Message]
    .by(uint16)
    .caseP(16) { case Message.Init(m) => m }(Message.Init.apply)(init.init)
    .caseP(18) { case Message.Ping(m) => m }(Message.Ping.apply)(ping.ping)
    .caseP(19) { case Message.Pong(m) => m }(Message.Pong.apply)(ping.pong)
    .caseP(256) { case Message.ChannelAnnouncement(m) => m }(
      Message.ChannelAnnouncement.apply
    )(gossip.channelAnnouncement)
    .caseP(257) { case Message.NodeAnnouncement(m) => m }(
      Message.NodeAnnouncement.apply
    )(gossip.nodeAnnouncement)
    .caseP(263) { case Message.QueryChanellRange(m) => m }(
      Message.QueryChanellRange.apply
    )(gossip.queryChannelRange)
    .caseP(264) { case Message.ReplyChannelRange(m) => m }(
      Message.ReplyChannelRange.apply
    )(gossip.replyChannelRange)
    .caseP(265) { case Message.GossipTimestampFilter(m) => m }(
      Message.GossipTimestampFilter.apply
    )(gossip.gossipTimestampFilter)

enum Response:
  case Send(m: Message)
  case Ignore

def decode(b: ByteVector): Either[String, Message] =
  messageCodec.decodeValue(b.toBitVector).toEither.left.map(_.toString)

def encode(m: Message): ByteVector =
  messageCodec.encode(m).getOrElse(???).toByteVector
