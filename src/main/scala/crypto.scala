package perun.crypto

import scala.annotation.targetName

import org.bitcoins.crypto.{ECDigitalSignature, ECPrivateKey, ECPublicKey}
import scodec.bits.ByteVector
import scodec.codecs.bytes
import scodec.{Attempt, Codec, Err}
import zio.UIO

enum DecryptionError:
  case BadTag

opaque type PrivateKey = ECPrivateKey

opaque type PublicKey = ECPublicKey

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
  val codec: Codec[Signature] =
    bytes(64).xmap(ECDigitalSignature.fromBytes, _.bytes)
