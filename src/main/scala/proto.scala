package perun.proto

import scodec.Codec
import scodec.bits.*
import scodec.codecs.*

// export init.Init
// export types.features.Features

enum Message:
  case Init(m: init.Init)

val messageCodec: Codec[Message] =
  discriminated[Message]
    .by(uint16)
    .caseP(1) { case Message.Init(m) => m }(Message.Init.apply)(init.init)

def decode(b: ByteVector): Either[String, Message] =
  messageCodec.decodeValue(b.toBitVector).toEither.left.map(_.toString)
