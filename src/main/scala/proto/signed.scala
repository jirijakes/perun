package perun.proto.signed

import fr.acinq.secp256k1.Secp256k1
import org.bitcoins.crypto.CryptoUtil.doubleSHA256
import org.bitcoins.crypto.{ECDigitalSignature, ECPublicKey}
import scodec.*
import scodec.bits.*
import scodec.codecs.*

import perun.proto.codecs.*

/** Derive a new codec of `A` which verifies a signature included at the
  * beginning of the message with public key obtained by `key` function.
  *
  * The type `A` must have as first element a value of type [[Signature]].
  * Public key, against which the message will be verified, can be provided
  * externally or by calling a function on top of the decoded message.
  *
  * @example
  * Having following message:
  *
  * ```
  * case class Message(
  *   signature: Signature,
  *   timestamp: Long,
  *   nodeId: NodeId
  * )
  * ```
  *
  * we can write its verifying codec:
  *
  * ```
  * val codec: Codec[Message] =
  *   signed[Message].withKey(_.nodeId.publicKey)(
  *     signature :: uint32 :: nodeId
  *   )
  * ```
  *
  * This codec verifies during decoding and encoding that the message `timestamp|nodeId`
  * (i. e. all fields behind signature) is correctly signed by public key's private key and
  * that the signature is valid.
  */
def signed[A] = new SignedPartialApply[A]

def signet[A](skip: Int)(fs: (A => (Signature, ECPublicKey))*)(codec: Codec[A]): Codec[A] = ???

class SignedPartialApply[A]:
  def withKey[B <: Tuple](key: A => ECPublicKey)(codec: Codec[Signature *: B])(
      using Iso[A, Signature *: B]
  ): Codec[A] = new Signed[A, B](codec, key)
  def withTwoKeys[B <: Tuple](key1: A => ECPublicKey, key2: A => ECPublicKey)(
      codec: Codec[Signature *: Signature *: B]
  )(using
      Iso[A, Signature *: Signature *: B]
  ): Codec[A] = new Signed2[A, B](codec, key1, key2)

private final class Signed[A, B <: Tuple](
    codec: Codec[Signature *: B],
    key: A => ECPublicKey
)(using iso: Iso[A, Signature *: B])
    extends Codec[A]:
  def sizeBound: SizeBound = codec.sizeBound
  def encode(value: A): Attempt[BitVector] = codec.encode(iso.to(value))
  def decode(bits: BitVector): Attempt[DecodeResult[A]] =
    codec.decode(bits) match
      case Attempt.Successful(DecodeResult(t @ (signature *: _), rem)) =>
        val message = bits.drop(512).dropRight(rem.length).toByteVector
        val a = iso.from(t)
        val valid = Secp256k1
          .get()
          .verify(
            signature.digitalSignature.bytes.toArray,
            doubleSHA256(message).bytes.toArray,
            key(a).bytes.toArray
          )
        if valid then Attempt.Successful(DecodeResult(a, rem))
        else Attempt.Failure(Err("NOT VALID!"))
      case f: Attempt.Failure => f

private final class Signed2[A, B <: Tuple](
    codec: Codec[Signature *: Signature *: B],
    key1: A => ECPublicKey,
    key2: A => ECPublicKey
)(using iso: Iso[A, Signature *: Signature *: B])
    extends Codec[A]:
  def sizeBound: SizeBound = codec.sizeBound
  def encode(value: A): Attempt[BitVector] = codec.encode(iso.to(value))
  def decode(bits: BitVector): Attempt[DecodeResult[A]] =
    codec.decode(bits) match
      case Attempt.Successful(
            DecodeResult(t @ (signature1 *: signature2 *: _), rem)
          ) =>
        val message = bits.drop(2048).dropRight(rem.length).toByteVector
        val a = iso.from(t)
        val valid1 = Secp256k1
          .get()
          .verify(
            signature1.digitalSignature.bytes.toArray,
            doubleSHA256(message).bytes.toArray,
            key1(a).bytes.toArray
          )
        val valid2 = Secp256k1
          .get()
          .verify(
            signature2.digitalSignature.bytes.toArray,
            doubleSHA256(message).bytes.toArray,
            key2(a).bytes.toArray
          )
        if valid1 && valid2 then Attempt.Successful(DecodeResult(a, rem))
        else Attempt.Failure(Err("NOT VALID!"))
      case f: Attempt.Failure => f
