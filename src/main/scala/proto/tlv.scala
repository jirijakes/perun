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

def tlv[C](t1: (Long, Codec[C])): Codec[Option[C]] =
  tlvStream.exmap(
    (_: Map[Long, ByteVector])
      .get(t1._1)
      .fold(Attempt.successful(None))(v =>
        t1._2.decodeValue(v.toBitVector).map(Some(_))
      ),
    _.fold(Attempt.successful(Map.empty))(c =>
      t1._2.encode(c).map(v => Map(t1._1 -> v.toByteVector))
    )
  )

def tlv[C1, C2](
    t1: (Long, Codec[C1]),
    t2: (Long, Codec[C2])
): Codec[(Option[C1], Option[C2])] =
  tlvStream.exmap(
    (in: Map[Long, ByteVector]) =>
      for
        c1 <- in
          .get(t1._1)
          .fold(Attempt.successful(None))(v =>
            t1._2.decodeValue(v.toBitVector).map(Some(_))
          )
        c2 <- in
          .get(t2._1)
          .fold(Attempt.successful(None))(v =>
            t2._2.decodeValue(v.toBitVector).map(Some(_))
          )
      yield (c1, c2),
    (c1, c2) =>
      for
        m1 <- c1.fold(Attempt.successful(Map.empty))(c =>
          t1._2.encode(c).map(v => Map(t1._1 -> v.toByteVector))
        )
        m2 <- c2.fold(Attempt.successful(Map.empty))(c =>
          t2._2.encode(c).map(v => Map(t2._1 -> v.toByteVector))
        )
      yield m1 ++ m2
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
