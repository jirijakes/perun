package perun.crypto

import scodec.bits.ByteVector
import scala.annotation.targetName

enum DecryptionError:
  case BadTag

opaque type PublicKey = ByteVector
opaque type PrivateKey = (ByteVector, PublicKey)
opaque type Signature = ByteVector
opaque type DoubleSHA256 = ByteVector

object PublicKey:
  def fromBytes(b: ByteVector): PublicKey = b

object PrivateKey:
  def fromHex(h: String): PrivateKey = ??? //ByteVector.fromValidHex(h)

extension (k: PublicKey)
  @targetName("publicKeyToArray")
  def toArray: Array[Byte] = k.toArray
  @targetName("publicKeyLength")
  def length: Int = k.length.toInt
  @targetName("publicKeyToBytes")
  def bytes: ByteVector = k

extension (k: PrivateKey)
  @targetName("privateKeyToArray")
  def toArray: Array[Byte] = k._1.toArray
  def publicKey: PublicKey = k._2

extension (s: Signature)
  @targetName("signatureToArray")
  def toArray: Array[Byte] = s.toArray

extension (d: DoubleSHA256)
  @targetName("doubleSHA256ToArray")
  def toArray: Array[Byte] = d.toArray
