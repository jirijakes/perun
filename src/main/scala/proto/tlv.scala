package perun.proto.tlv

import scodec.bits.*
import scodec.codecs.*
import scodec.{Attempt, Codec, Err}

import perun.proto.codecs.*
import perun.proto.uint64.*

/** Single field of TLV stream.
  *
  * @param tag type of record
  * @param content raw (encoded) value
  */
final case class Record(tag: Long, content: ByteVector)

/** Series of raw (encoded) TLV records. */
opaque type TlvStream = Map[Long, ByteVector]

extension (s: TlvStream)
  /** Obtain raw (encoded) record of type `tag`.
    *
    * @param tag type of TLV record
    * @return raw (encoded) record if found, otherwise `None`
    */
  def find(tag: Long): Option[ByteVector] = s.get(tag)

  /** Add value and its type to the stream.
    *
    * @param tag type of record
    * @param value value
    * @return updated TLV stream
    */
  def put(tag: Long, value: ByteVector): TlvStream = s.updated(tag, value)

object TlvStream:
  def apply(m: Map[Long, ByteVector]): TlvStream = m
  def empty: TlvStream = Map.empty

/** Match type that converts TLV `type -> codec` pairs into options
  * of codec types. For example, `((Long, Codec[Int]), (Long, Codec[String]))`
  * will become `(Option[Int], Option[String])`.
  */
type Tlv[Mapping <: Tuple] <: Tuple = Mapping match
  case (Long, Codec[t]) *: rest => Option[t] *: Tlv[rest]
  case (Long, Codec[t])         => Option[t] *: EmptyTuple
  case EmptyTuple               => EmptyTuple

extension [A](codec: Codec[A])
  /** Validate value encoded by this codec using provided function.
    * Decoding is not modified in any way.
    *
    * @param validate validating function
    * @return derived codec that validates encoded value
    */
  def validateEncoding(validate: A => Either[String, A]): Codec[A] =
    codec.exmap(
      a =>
        validate(a) match
          case Left(error)  => Attempt.Failure(Err(error))
          case Right(valid) => Attempt.Successful(valid)
      ,
      Attempt.successful
    )

/** Codec for one encoded TLV record. */
val record: Codec[Record] =
  ("type" | bigsize ::
    variableSizeBytesLong("length" | bigsize, "value" | bytes)).as[Record]

/** Codec for encoded TLV stream (`tlv_stream` type). This codec does not decode values,
  * for decoded values (that is what is typically needed when implementing BOLT protocol),
  * see [[tlv]].
  */
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

/** Codec for decoded TLV stream. Values are decoded using the provided `mapping`, where each
  * possible type is described in form `type -> codec`. Type of decoded value is a tuple
  * with the same number of members as there are types and their types corresponding,
  * in order, to types of provided codecs.
  *
  * @example {{{
  * val myCodec: Codec[(Option[Int], Option[String])] =
  *   tlv(0L -> uint16, 1L -> utf8)
  * }}}
  *
  * @param mapping mapping of types to codec
  * @return new codec
  */
inline def tlv[Mapping <: Tuple](mapping: Mapping): Codec[Tlv[Mapping]] =
  tlvStream.exmap(runtime.encodeStream(mapping), runtime.decodeStream(mapping))

private object runtime:

  def encodeStream[Mapping <: Tuple](
      mapping: Mapping
  )(stream: TlvStream): Attempt[Tlv[Mapping]] =
    mapping.toIArray
      .map { case (tag: Long, codec: Codec[?]) =>
        stream
          .find(tag)
          .fold(Attempt.successful(Option.empty))(bytes =>
            codec.decodeValue(bytes.toBitVector).map(Option(_))
          )
      }
      .foldLeft(Attempt.successful(IArray.emptyObjectIArray))((acc, cur) =>
        acc.flatMap(arr => cur.map(c => arr :+ c))
      )
      .map(arr =>
        scala.runtime.Tuples.fromIArray(arr).asInstanceOf[Tlv[Mapping]]
      )

  def decodeStream[Mapping <: Tuple](
      mapping: Mapping
  )(tlv: Tlv[Mapping]): Attempt[TlvStream] =
    mapping
      .zip(tlv)
      .toIArray
      .collect { case ((tag: Long, codec: Codec[t]), Some(value)) =>
        codec
          .encode(value.asInstanceOf[t])
          .map(bits => (tag, bits.toByteVector))
      }
      .foldLeft(Attempt.successful(TlvStream.empty))((acc, cur) =>
        acc.flatMap(macc => cur.map(macc.put))
      )

end runtime
