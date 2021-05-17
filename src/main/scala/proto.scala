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

val messageCodec: Codec[Message] =
  discriminated[Message]
    .by(uint16)
    .caseP(16) { case Message.Init(m) => m }(Message.Init.apply)(init.init)
    .caseP(18) { case Message.Ping(m) => m }(Message.Ping.apply)(ping.ping)
    .caseP(19) { case Message.Pong(m) => m }(Message.Pong.apply)(ping.pong)

enum Response:
   case Send(m: Message)
   case Ignore

def decode(b: ByteVector): Either[String, Message] =
  messageCodec.decodeValue(b.toBitVector).toEither.left.map(_.toString)

def encode(m: Message): ByteVector =
  messageCodec.encode(m).getOrElse(???).toByteVector
