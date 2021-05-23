package perun.proto

import scala.math.BigDecimal.long2bigDecimal
import scala.math.Ordering.Implicits.infixOrderingOps

import scodec.*
import scodec.bits.{BitVector, ByteVector}
import scodec.codecs.*

/*
 * BIG THANKS TO ACINQ/ECLAIR.
 */

opaque type UInt64 = Long

object UInt64:
  def apply(b: ByteVector): UInt64 = b.toLong(signed = false)
  def apply(i: Int): UInt64 = i.toLong
  def apply(l: Long): UInt64 = l

  extension (u: UInt64) def toByteVector = ByteVector.fromLong(u)

  /** Ensures that value is minimally encoded. “A value is said to be minimally encoded
    * if it could not be encoded using fewer bytes.”
    *
    * @see https://github.com/lightningnetwork/lightning-rfc/blob/master/01-messaging.md#appendix-a-bigsize-test-vectors
    *
    * @param codec codec to modify
    * @param min minimal allowable value
    */
  def minimally[A: Ordering](codec: Codec[A], min: A): Codec[A] =
    codec.exmap(
      i =>
        if i < min then Attempt.failure(Err("value was not minimally encoded"))
        else Attempt.successful(i),
      Attempt.successful
    )

  val maxValue = UInt64(ByteVector.fromValidHex("ffffffffffffffff"))

  val uint64: Codec[UInt64] =
    bytes(8).xmap(b => UInt64(b), a => a.toByteVector.padLeft(8))

  /** Discriminator codec with a default fallback codec (of the same type).
    */
  def discriminatorWithDefault[A](
      discriminator: Codec[A],
      fallback: Codec[A]
  ): Codec[A] = new Codec[A]:
    def sizeBound: SizeBound = discriminator.sizeBound | fallback.sizeBound
    def encode(e: A): Attempt[BitVector] =
      discriminator.encode(e).recoverWith { case _ => fallback.encode(e) }
    def decode(b: BitVector): Attempt[DecodeResult[A]] =
      discriminator.decode(b).recoverWith {
        case _: KnownDiscriminatorType[?]#UnknownDiscriminator =>
          fallback.decode(b)
      }

  // TODO: Does this need its own value? Is this needed or should be joined with `bigsize`?
  val bigsize64: Codec[UInt64] =
    discriminatorWithDefault(
      discriminated[UInt64]
        .by(uint8L)
        .subcaseP(0xff) { case i if i >= 0x100000000L => i }(
          minimally(uint64, 0x100000000L)
        )
        .subcaseP(0xfe) { case i if i >= 0x10000 => i }(
          minimally(uint32, 0x10000)
        )
        .subcaseP(0xfd) { case i if i >= 0xfd => i }(
          minimally(uint16.xmap(_.toLong, _.toInt), 0xfd)
        ),
      uint8L.xmap(_.toLong, _.toInt)
    )

  /** Variable-length integer compatible with Bitcoin's
    * [[https://btcinformation.org/en/developer-reference#compactsize-unsigned-integers CompactSize]].
    * The total size can be 1, 3, 5 or 9 bytes depending on the size of the integer. All values are encoded as big-endian.
    *
    * This version can only handle signed 64-bit values.
    *
    * The value x will be encoded as:
    *
    * ```text
    *         uint8(x) if x < 0fd (253)
    * 0xfd | uint16(x) if x < 0x10000 (65_536)
    * 0xfe | uint32(x) if x < 0x100000000 (4_294_967_296)
    * 0xff | uint64(x) otherwise
    * ```
    */
  val bigsize: Codec[Long] = bigsize64.narrow(
    l =>
      if l <= Long.MaxValue then Attempt.successful(l)
      else Attempt.failure(Err(s"overflow for value $l")),
    identity
  )

  val bigsizeBits: Codec[BitVector] =
    bigsize.xmap(_.toByteVector.toBitVector, bi => apply(bi.toByteVector))

  // format: off
  val tbigsize: Codec[UInt64] = Codec(
    u => {
      val b = u match {
        case u if u < 0x01                => ByteVector.empty
        case u if u < 0x0100              => u.toByteVector.takeRight(1)
        case u if u < 0x010000            => u.toByteVector.takeRight(2)
        case u if u < 0x01000000          => u.toByteVector.takeRight(3)
        case u if u < 0x0100000000L       => u.toByteVector.takeRight(4)
        case u if u < 0x010000000000L     => u.toByteVector.takeRight(5)
        case u if u < 0x01000000000000L   => u.toByteVector.takeRight(6)
        case u if u < 0x0100000000000000L => u.toByteVector.takeRight(7)
        case u /*if u <= UInt64.maxValue*/    => u.toByteVector.takeRight(8)
      }
      Attempt.successful(b.bits)
    },
    b => b.length match {
      case l if l <= 0  => minimally(uint64, 0x00L).decode(b.padLeft(64))
      case l if l <= 8  => minimally(uint64, 0x01L).decode(b.padLeft(64))
      case l if l <= 16 => minimally(uint64, 0x0100L).decode(b.padLeft(64))
      case l if l <= 24 => minimally(uint64, 0x010000L).decode(b.padLeft(64))
      case l if l <= 32 => minimally(uint64, 0x01000000L).decode(b.padLeft(64))
      case l if l <= 40 => minimally(uint64, 0x0100000000L).decode(b.padLeft(64))
      case l if l <= 48 => minimally(uint64, 0x010000000000L).decode(b.padLeft(64))
      case l if l <= 56 => minimally(uint64, 0x01000000000000L).decode(b.padLeft(64))
      case l if l <= 64 => minimally(uint64, 0x0100000000000000L).decode(b.padLeft(64))
      case _ => Attempt.failure(Err(s"too many bytes to decode for truncated uint64 (${b.toHex})"))
    }
  )
  // format: on
