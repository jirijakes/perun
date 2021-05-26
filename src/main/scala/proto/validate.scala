package perun.proto.validate

import org.bitcoins.crypto.CryptoUtil.doubleSHA256
import org.bitcoins.crypto.{ECDigitalSignature, ECPublicKey}
import scodec.*
import scodec.bits.BitVector

import perun.proto.codecs.*
import perun.proto.gossip.*

def validated[T](codec: Codec[T])(v: Codec[T] => Codec[T]): Codec[T] = v(codec)

def signed[T](
    skipBits: Long,
    sig: T => Signature,
    key: T => ECPublicKey
)(codec: Codec[T]): Codec[T] =
  new Signed[T](skipBits, codec, (sig, key))

def signed[T](
    skipBits: Long,
    sig1: T => Signature,
    key1: T => ECPublicKey,
    sig2: T => Signature,
    key2: T => ECPublicKey
)(codec: Codec[T]): Codec[T] =
  new Signed[T](skipBits, codec, (sig1, key1), (sig2, key2))

private class Signed[T](
    skipBits: Long,
    codec: Codec[T],
    fs: (T => Signature, T => ECPublicKey)*
) extends Codec[T]:
  def sizeBound: SizeBound = codec.sizeBound
  def encode(value: T): Attempt[BitVector] = codec.encode(value)
  def decode(bits: BitVector): Attempt[DecodeResult[T]] =
    codec.decode(bits) match
      case f: Attempt.Failure => f
      case s @ Attempt.Successful(DecodeResult(t, rem)) =>
        val witness = doubleSHA256(
          bits.drop(skipBits).dropRight(rem.length).toByteVector
        )
        println(">>>>>>>>>>> " + t)
        val isValid = fs.iterator
          .map((sig, key) => true
            // Secp256k1
              // .get()
              // .verify(
                // sig(t).digitalSignature.bytes.toArray,
                // witness.bytes.toArray,
                // key(t).bytes.toArray
              // )
          )
          .forall(_ == true)
        if isValid then s else Attempt.Failure(Err("NOT VALID"))
