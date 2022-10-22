package perun.proto.tlv

import scodec.bits.*
import scodec.codecs.*
import scodec.{Attempt, Codec, Err}

import perun.proto.codecs.*
import perun.proto.uint64.*

opaque type TlvStream = Map[Long, ByteVector]

extension (s: TlvStream) def find(tag: Long): Option[ByteVector] = s.get(tag)

object TlvStream:
  def apply(m: Map[Long, ByteVector]): TlvStream = m
  def empty: TlvStream = Map.empty

type Tlv[T <: Tuple] <: Tuple = T match
  case (Long, Codec[h])      => Option[h] *: EmptyTuple
  case (Long, Codec[h]) *: t => Option[h] *: Tlv[t]
  case EmptyTuple            => EmptyTuple

def tlv[T <: Tuple](t: T): Codec[Tlv[T]] =
  tlvStream.exmap(mapToTlv(t), tlvToMap(t))

private def mapToTlv[T <: Tuple](t: T)(m: TlvStream): Attempt[Tlv[T]] =
  t.toIArray
    .map { case (i: Long, c: Codec[?]) =>
      m.find(i)
        .fold(Attempt.successful(Option.empty))(b =>
          c.decodeValue(b.toBitVector).map(Option(_))
        )
    }
    .foldLeft(Attempt.successful(IArray.emptyObjectIArray))((acc, cur) =>
      acc.flatMap(arr => cur.map(c => arr :+ c))
    )
    .map(arr => runtime.Tuples.fromIArray(arr).asInstanceOf[Tlv[T]])

private def tlvToMap[T <: Tuple](t: T)(tt: Tlv[T]): Attempt[TlvStream] =
  t.zip(tt)
    .toIArray
    .map { case ((a: Long, b: Codec[h]), c: Option[?]) =>
      b.encode(c.asInstanceOf[Option[h]].get).map(b => (a, b))
    }
    .foldLeft(Attempt.successful(Map.empty[Long, ByteVector]))((acc, cur) =>
      acc.flatMap(macc => cur.map((i, b) => macc.updated(i, b.toByteVector)))
    )

final case class Record(tag: Long, content: ByteVector)

val record: Codec[Record] =
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

val tlvStream: Codec[TlvStream] =
  vector(record)
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
