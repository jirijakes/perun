package perun.proto.init

import scodec.*
import scodec.codecs.*
import scodec.bits.ByteVector

import perun.proto.codecs.*
import perun.proto.features.*
import perun.proto.tlv.*

final case class Init(features: Features, s: List[String])

val tlvNetworks: Codec[List[String]] =
  list(bytes(32).xmap(_.toHex, ByteVector.fromValidHex(_)))

val tlvInit: Codec[List[String]] = tlvStream.exmap(
  x =>
    for
      a <- Attempt.fromOption(x.find(1), Err("aaaaa"))
      b <- tlvNetworks.decode(a.toBitVector)
    yield b.value,
  y => tlvNetworks.encode(y).map(b => TlvStream(Map((1L, b.toByteVector))))
)

val init: Codec[Init] =
  (("features" | features) :: ("tlv_stream" | tlvInit)).as[Init]
