package perun.proto.generic

import scodec.Codec
import scodec.bits.ByteVector
import scodec.codecs.*
import perun.proto.blockchain.*
import perun.proto.codecs.*
import perun.proto.features.*

import scala.deriving.*
import scala.compiletime.*

/** Type class indicating which codecs can be automatically
  * derived using [[proto]]. This type class is meant to be
  * sealed, it has to be carefully curated and no overriding
  * can be allowed due to sensitivities of parsing binary data.
  */
sealed trait Proto[A]:
  def codec: Codec[A]

object Proto:
  given Proto[Msat] with
    def codec = msat
  given Proto[Color] with
    def codec = color
  given Proto[Signature] with
    def codec = signature
  given Proto[Features] with
    def codec = features
  given Proto[Timestamp] with
    def codec = timestamp
  given Proto[ShortChannelId] with
    def codec = shortChannelId
  given Proto[NodeId] with
    def codec = nodeId
  given Proto[Alias] with
    def codec = alias
  given Proto[Address] with
    def codec = address
  given Proto[Chain] with
    def codec = chain
  given Proto[ByteVector] with
    def codec = bytes
  given [T](using c: Proto[T]): Proto[Vector[T]] with
    def codec = vector(variableSizeBytes(uint16, c.codec))

/** Derive codec for `T`. The set of types that can be used for
  * derivation is limited to instances of [[Proto]]. This set
  * assumes using distinct types for different purposes, e. g.
  * field representing millisats has to be represented as a value
  * of type [[Msat]] as opposed to `Long` or `BigInt`.
  *
  * @example
  *
  * ```scala
  * case class Message(time: Timestamp, id: NodeId)
  *
  * val message: Codec[Message] = proto[Message]
  * ```
  */
inline def proto[T](using m: Mirror.Of[T]): Codec[T] =
  summonAll[m.MirroredElemTypes, m.MirroredElemLabels].iterator
    .reduce(_ :: _)
    .asInstanceOf[Codec[T]]

private inline def summonAll[T <: Tuple, L <: Tuple]: List[Codec[?]] =
  inline (erasedValue[T], erasedValue[L]) match
    case (_: EmptyTuple, _: EmptyTuple) => Nil
    case (_: (t *: ts), _: (l *: ls)) =>
      val label = constValue[l].toString
        .replaceAll("""(\b[a-z]+|\G(?!^))((?:[A-Z]|\d+)[a-z]*)""", """$1_$2""")
        .toLowerCase
      summonInline[Proto[t]].codec.withContext(label) :: summonAll[ts, ls]
