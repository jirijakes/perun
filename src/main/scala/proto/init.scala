package perun.proto.init

import scodec.*
import scodec.bits.ByteVector
import scodec.codecs.*

import perun.proto.blockchain.*
import perun.proto.codecs.*
import perun.proto.features.*

final case class Init(
    features: Flags[Feature],
    networks: Option[List[Chain]],
    remoteAddress: Option[Address]
)

val initFeatures: Codec[ByteVector] =
  (
    variableSizeBytes("gflen" | uint16, bytes) ::
      variableSizeBytes("flen" | uint16, bytes)
  ).xmapc { (gf, f) =>
    val l = gf.length.max(f.length)
    gf.padLeft(l) | f.padLeft(l)
  } { f => (ByteVector.empty, f) }

val init: Codec[Init] =
  (
    ("features" | flags(initFeatures)) ::
      ("tlv_stream" | tlv(1L -> list(chain), 3L -> address))
  ).as[Init]
