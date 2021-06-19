import scodec.bits.ByteVector

import scodec.*
import scodec.codecs.*

val c = ("a" | uint8) :: ("b" | uint8) :: ("c" | variableSizeBytes("d" | uint8, "e" | bytes))

// val e = c.encode((4, ByteVector(1, 2, 3)))

val b = ByteVector(0, 5, 2, 9, 8).toBitVector

val d = c.decode(b)

val l = d.getOrElse(???).log

