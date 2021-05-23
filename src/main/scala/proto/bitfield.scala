package perun.proto.bitfield

import scodec.*
import scodec.bits.BitVector

// List(true, false, false) => 100
class Bitfield[A](fs: List[A => Boolean]) extends Codec[A]:
  def sizeBound: SizeBound = perun.proto.uint64.bigsize.sizeBound
  def encode(value: A): Attempt[BitVector] =
    Attempt.Successful(BitVector.bits(fs.map(_(value))))
  def decode(bits: BitVector): Attempt[DecodeResult[A]] = ???

// def bitfield[A]


// val prd: Codec[Flags] =
object Main:
  import macros.Bitfield.*

  def main(a: Array[String]): Unit =
    val x = bitfield[Flax](_.isRed, _.isDotted)
    println(x)
