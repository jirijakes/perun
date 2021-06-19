/*
 * Copyright (c) 2013, Scodec
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors
 *    may be used to endorse or promote products derived from this software without
 *    specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package scodec
package codecs

import java.security.Key
import java.security.spec.AlgorithmParameterSpec
import javax.crypto.{BadPaddingException, Cipher, IllegalBlockSizeException}

import scodec.bits.BitVector

/**
  * Represents the ability to create a `Cipher` for encryption or decryption.
  *
  * Used in conjunction with [[encrypted]].
  */
trait CipherFactory:

  /** Creates a cipher initialized for encryption. */
  def newEncryptCipher: Cipher

  /** Creates a cipher initialized for decryption. */
  def newDecryptCipher: Cipher

/**
  * Companion for [[CipherFactory]].
  */
object CipherFactory:

  /**
    * Creates a cipher factory for the specified transformation (via `Cipher.getInstance(transformation)`)
    * and using the specified functions for initializing for encryption and decryption respectively.
    */
  def apply(
      transformation: String,
      initForEncryption: Cipher => Unit,
      initForDecryption: Cipher => Unit
  ): CipherFactory =
    new SimpleCipherFactory(transformation, initForEncryption, initForDecryption)

  /**
    * Creates a cipher factory for the specified transformation (via `Cipher.getInstance(transformation)`).
    *
    * Ciphers are initialized with the specified key only.
    */
  def apply(transformation: String, key: Key): CipherFactory =
    new SimpleCipherFactory(
      transformation,
      _.init(Cipher.ENCRYPT_MODE, key),
      _.init(Cipher.DECRYPT_MODE, key)
    )

  /**
    * Creates a cipher factory for the specified transformation (via `Cipher.getInstance(transformation)`).
    *
    * Ciphers are initialized with the specified key and algorithm parameter specification.
    */
  def apply(transformation: String, key: Key, spec: AlgorithmParameterSpec): CipherFactory =
    new SimpleCipherFactory(
      transformation,
      _.init(Cipher.ENCRYPT_MODE, key, spec),
      _.init(Cipher.DECRYPT_MODE, key, spec)
    )

  private class SimpleCipherFactory(
      transformation: String,
      initForEncryption: Cipher => Unit,
      initForDecryption: Cipher => Unit
  ) extends CipherFactory:

    private def newCipher: Cipher = Cipher.getInstance(transformation).nn

    def newEncryptCipher: Cipher =
      val cipher = newCipher
      initForEncryption(cipher)
      cipher

    def newDecryptCipher: Cipher =
      val cipher = newCipher
      initForDecryption(cipher)
      cipher


/** @see [[encrypted]] */
private[codecs] final class CipherCodec[A](codec: Codec[A], cipherFactory: CipherFactory)
    extends Codec[A]:

  override def sizeBound = SizeBound.unknown

  override def encode(a: A) =
    codec.encode(a).flatMap(b => encrypt(b))

  private def encrypt(bits: BitVector) =
    val blocks = bits.toByteArray
    try
      val encrypted = cipherFactory.newEncryptCipher.doFinal(blocks).nn
      Attempt.successful(BitVector(encrypted))
    catch
      case _: IllegalBlockSizeException =>
        Attempt.failure(Err(s"Failed to encrypt: invalid block size ${blocks.size}"))

  override def decode(buffer: BitVector) =
    decrypt(buffer).flatMap { result =>
      codec.decode(result).map {
        _.mapRemainder(_ => BitVector.empty)
      }
    }

  private def decrypt(buffer: BitVector): Attempt[BitVector] =
    val blocks = buffer.toByteArray
    try
      val decrypted = cipherFactory.newDecryptCipher.doFinal(blocks).nn
      Attempt.successful(BitVector(decrypted))
    catch
      case e @ (_: IllegalBlockSizeException | _: BadPaddingException) =>
        Attempt.failure(Err("Failed to decrypt: " + e.getMessage))

  override def toString = s"cipher($codec)"
