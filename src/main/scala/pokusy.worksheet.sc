import scodec.*
import scodec.bits.*

val v = ByteVector(1, 2, 3, 4, 5, 6, 7)

v.splitAt(v.length - 2)

import perun.proto.*

val c = scodec.codecs.list(tlv.codec)

val x = c.decode(
  BitVector.fromValidHex(
    "0331023da092f6980e58d2c037173180e9a465476026ee50f96695963e8efe436f54eb00000000000000010000000000000002"
  )
)

x match
  case Attempt.Successful(DecodeResult(s, x)) =>
    x

"s"
