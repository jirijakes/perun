package perun.proto.init

import scodec.*
import scodec.bits.ByteVector
import scodec.codecs.*

import perun.proto.blockchain.*
import perun.proto.codecs.*
import perun.proto.features.*

final case class Init(
    features: Features,
    networks: Option[List[Chain]],
    remoteAddress: Option[Address]
)

val init: Codec[Init] =
  (
    ("features" | features2) ::
      ("tlv_stream" | tlv(1L -> list(chain), 3L -> address))
  ).as[Init]
