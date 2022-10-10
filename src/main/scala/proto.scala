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
  case ChannelAnnouncement(m: gossip.ChannelAnnouncement)
  case ChannelUpdate(m: gossip.ChannelUpdate)
  case OpenChannel(m: channel.OpenChannel)
  case AcceptChannel(m: channel.AcceptChannel)
  case FundingCreated(m: channel.FundingCreated)
  case FundingSigned(m: channel.FundingSigned)
  case FundingLocked(m: channel.FundingLocked)
  case Shutdown(m: channel.Shutdown)
  case ClosingSigned(m: channel.ClosingSigned)
  // case UpdateAddHtlc(m: channel.UpdateAddHtlc)

import Message.*

// format: off
val messageCodec: Codec[Message] = discriminated[Message].by(uint16)
  .caseP(16)  { case Init(m)                  => m }(Init.apply                  )(init.init                   )
  .caseP(18)  { case Ping(m)                  => m }(Ping.apply                  )(ping.ping                   )
  .caseP(19)  { case Pong(m)                  => m }(Pong.apply                  )(ping.pong                   )

  .caseP(32)  { case OpenChannel(m)           => m }(OpenChannel.apply           )(channel.openChannel         )
  .caseP(33)  { case AcceptChannel(m)         => m }(AcceptChannel.apply         )(channel.acceptChannel       )
  .caseP(34)  { case FundingCreated(m)        => m }(FundingCreated.apply        )(channel.fundingCreated      )
  .caseP(35)  { case FundingSigned(m)         => m }(FundingSigned.apply         )(channel.fundingSigned       )
  .caseP(36)  { case FundingLocked(m)         => m }(FundingLocked.apply         )(channel.fundingLocked       )
  .caseP(38)  { case Shutdown(m)              => m }(Shutdown.apply              )(channel.shutdown            )
  .caseP(39)  { case ClosingSigned(m)         => m }(ClosingSigned.apply         )(channel.closingSigned       )
  // .caseP(128) { case UpdateAddHtlc(m)         => m }(UpdateAddHtlc.apply         )(channel.updateAddHtlc       )

  .caseP(256) { case ChannelAnnouncement(m)   => m }(ChannelAnnouncement.apply   )(gossip.channelAnnouncement  )
  .caseP(257) { case NodeAnnouncement(m)      => m }(NodeAnnouncement.apply      )(gossip.nodeAnnouncement     )
  .caseP(258) { case ChannelUpdate(m)         => m }(ChannelUpdate.apply         )(gossip.channelUpdate        )
  .caseP(263) { case QueryChanellRange(m)     => m }(QueryChanellRange.apply     )(gossip.queryChannelRange    )
  .caseP(264) { case ReplyChannelRange(m)     => m }(ReplyChannelRange.apply     )(gossip.replyChannelRange    )
  .caseP(265) { case GossipTimestampFilter(m) => m }(GossipTimestampFilter.apply )(gossip.gossipTimestampFilter)
// format: on

// enum Response:
//   case Send(m: Message)
//   case Ignore
//   case FailConnection

def decode(b: ByteVector): Either[String, Message] =
  messageCodec.decodeValue(b.toBitVector).toEither.left.map(_.toString)

def encode(m: Message): ByteVector =
  messageCodec.encode(m).getOrElse(???).toByteVector
