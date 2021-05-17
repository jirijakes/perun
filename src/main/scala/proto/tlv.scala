package perun.proto.tlv

import scodec.{Attempt, Codec, Err}
import scodec.bits.*
import scodec.codecs.*

import perun.proto.codecs.*
import perun.proto.UInt64.*

opaque type TlvStream = Map[Long, ByteVector]

extension (s: TlvStream) def find(tag: Long): Option[ByteVector] = s.get(tag)

object TlvStream:
  def apply(m: Map[Long, ByteVector]): TlvStream = m

final case class Record(tag: Long, content: ByteVector)

val tlv: Codec[Record] =
  (bigsize :: variableSizeBytesLong(bigsize, bytes)).as[Record]

// format: off
extension [A](c: Codec[A])
  def validateEncoding[E](f: A => Either[String, A]): Codec[A] =
    c.exmap(a => f(a) match {
      case Left(s) => Attempt.Failure(Err(s))
      case Right(x) => Attempt.Successful(x)
    }, Attempt.successful
  )
// format: on

val tlvStream: Codec[TlvStream] = vector(tlv)
  .validateEncoding(v =>
    Either.cond(
      v.sliding(2).forall {
        case Seq(a, b) => a.tag > b.tag
        case _         => true
      },
      v,
      "types are not strictly increasing"
    )
  )
  .xmap(
    _.map(r => (r.tag, r.content)).toMap,
    _.toVector.map((t, c) => Record(t, c)).sortBy(_.tag)
  )
