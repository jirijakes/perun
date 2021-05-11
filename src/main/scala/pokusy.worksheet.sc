import scodec.bits._

val v = ByteVector(1, 2, 3, 4, 5, 6, 7)

v.splitAt(v.length - 2)

