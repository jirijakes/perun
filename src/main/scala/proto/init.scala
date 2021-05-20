package perun.proto.init

import scodec.*
import scodec.codecs.*
import scodec.bits.ByteVector

import perun.proto.codecs.*
import perun.proto.features.*
import perun.proto.blockchain.*
import perun.proto.tlv.*

final case class Init(features: Features, s: List[Chain])

val tlvNetworks: Codec[List[Chain]] = list(chain)

val tlvInit: Codec[List[Chain]] = tlvStream.exmap(
  x =>
    for
      a <- Attempt.fromOption(x.find(1), Err("aaaaa"))
      b <- tlvNetworks.decode(a.toBitVector)
    yield b.value,
  y => tlvNetworks.encode(y).map(b => TlvStream(Map((1L, b.toByteVector))))
)

val init: Codec[Init] =
  (("features" | features) :: ("tlv_stream" | tlvInit)).as[Init]
