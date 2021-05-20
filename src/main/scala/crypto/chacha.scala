package perun.crypto.chacha

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import javax.crypto.{Cipher, SecretKey}
import javax.crypto.spec.{IvParameterSpec, SecretKeySpec}
import scodec.bits.ByteVector

import perun.crypto.DecryptionError

def encrypt(
    key: ByteVector,
    nonce: BigInt,
    ad: ByteVector,
    plaintext: ByteVector
): ByteVector =
  val cipher = newCipher
  val n = Array.fill[Byte](4)(0) ++
    nonce.toByteArray.reverse.padTo[Byte](8, 0)
  val iv = new IvParameterSpec(n)

  // TODO: These can also throw exception
  cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key.toArray, "EC"), iv)
  cipher.updateAAD(ad.toArray)

  ByteVector.view(cipher.doFinal(plaintext.toArray))

def decrypt(
    key: ByteVector,
    nonce: BigInt,
    ad: ByteVector,
    ciphertext: ByteVector
): Either[DecryptionError, ByteVector] =
  val cipher = newCipher
  val n = Array.fill[Byte](4)(0) ++
    nonce.toByteArray.reverse.padTo[Byte](8, 0)
  val iv = new IvParameterSpec(n)

  // TODO: These can also throw exception
  cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key.toArray, "EC"), iv)
  cipher.updateAAD(ad.toArray)

  try Right(ByteVector.view(cipher.doFinal(ciphertext.toArray)))
  catch case _ => Left(DecryptionError.BadTag)

def newCipher: Cipher = Cipher.getInstance("ChaCha20-Poly1305")

def chachaPresent =
  try
    val _ = newCipher
    true
  catch case _ => false
