package perun.crypto

import scala.annotation.targetName

import org.bitcoins.crypto.{ECDigitalSignature, ECPrivateKey, ECPublicKey}
import scodec.bits.ByteVector
import scodec.codecs.bytes
import scodec.{Attempt, Codec, Err}
import zio.UIO

enum DecryptionError:
  case BadTag

/** Representation of elliptic curve secret key. In the context
  * of Lightning Network, it is always on Secp256k1 curve.
  */
opaque type PrivateKey = ECPrivateKey

/** Representation of elliptic curve public key. In the context
  * of Lightning Network, it is always on Secp256k1 curve.
  */
opaque type PublicKey = ECPublicKey

/** Representation of elliptic curve digital signature. In the context
  * of Lightning Network, it is always on Secp256k1 curve.
  */
opaque type Signature = ECDigitalSignature

extension (sec: PrivateKey)
  def toBytes: ByteVector = sec.bytes
  def publicKey: PublicKey = sec.publicKey

object PrivateKey:
  def freshPrivateKey: UIO[PrivateKey] =
    UIO.effectTotal(ECPrivateKey.freshPrivateKey)
  def fromHex(hex: String): PrivateKey = ECPrivateKey.fromHex(hex)

extension (pub: PublicKey)
  def asECPublicKey: ECPublicKey = pub
  def toBytes: ByteVector = pub.bytes
  def byteSize: Long = pub.byteSize

object PublicKey:
  def fromBytes(bytes: ByteVector): PublicKey = ECPublicKey.fromBytes(bytes)
  def fromHex(hex: String): PublicKey = ECPublicKey.fromHex(hex)
  val codec: Codec[PublicKey] = bytes(33).exmap(
    b =>
      ECPublicKey
        .fromBytesOpt(b)
        .fold(Attempt.Failure(Err("Invalid public key")))(Attempt.successful),
    k => Attempt.successful(k.bytes)
  )
  given noise.Binary[PublicKey] = _.compressed.bytes

// extension (s: Signature) def digitalSignature: ECDigitalSignature = s

object Signature:
  def fromBytes(b: ByteVector): Signature = ECDigitalSignature.fromBytes(b)
  val dummy: Signature = ECDigitalSignature(ByteVector.fill[Byte](64)(0))
  val codec: Codec[Signature] =
    bytes(64).xmap(ECDigitalSignature.fromBytes, _.bytes)
