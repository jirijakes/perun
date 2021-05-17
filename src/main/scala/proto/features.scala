package perun.proto.features

import scodec.Codec
import scodec.codecs.*
import scodec.bits.ByteVector

opaque type Features = ByteVector

// TODO: remove
object Features:
    def apply(b: ByteVector): Features = b

enum Feature(name: String, description: String):
  case InitialRoutingSync
      extends Feature(
        "initial_routing_sync",
        "Sending node needs a complete routing information dump"
      )

val features: Codec[Features] =
  (
    variableSizeBytes("gflen" | uint16, bytes) ::
      variableSizeBytes("flen" | uint16, bytes)
  ).xmapc { (gf, f) =>
    val l = gf.length.max(f.length)
    gf.padLeft(l) | f.padLeft(l)
  } { f => (ByteVector.empty, f) }
