// https://github.com/calvinmetcalf/chacha20poly1305/blob/master/test/fixtures.json

package perun.crypto.chacha

import io.circe.{Decoder, Json, parser}
import scodec.bits.ByteVector
import zio.*
import zio.blocking.*
import zio.stream.*
import zio.test.Assertion.*
import zio.test.*

object test extends DefaultRunnableSpec:

  given Decoder[ByteVector] = Decoder[String].map(s =>
    ByteVector.fromHex(s).getOrElse(ByteVector(s.getBytes))
  )

  final case class Test(
      key: ByteVector,
      nonce: ByteVector,
      plaintext: ByteVector,
      ad: ByteVector,
      ciphertext: ByteVector,
      tag: ByteVector
  )

  given Decoder[Test] =
    Decoder.forProduct6("KEY", "NONCE", "IN", "AD", "CT", "TAG")(Test.apply)

  val vector: Gen[Blocking, Test] =
    Gen
      .fromEffect(
        ZStream
          .fromResource("perun/crypto/chacha_test_vector.json")
          .transduce(ZTransducer.utf8Decode)
          .runCollect
          .flatMap(s => ZIO.fromEither(parser.parse(s.mkString)))
          .flatMap(j => ZIO.fromEither(j.as[Vector[Test]]))
          .orDie
      )
      .flatMap(Gen.fromIterable(_))

  val spec =
    suite("crypto")(
      suite("chacha")(
        suite("ChaCha20-Poly1305")(
          testM("encrypt") {
            checkAll(vector)(t =>
              assert(encrypt(t.key, t.nonce, t.ad, t.plaintext))(
                equalTo(t.ciphertext ++ t.tag)
              )
            )
          },
          testM("decrypt") {
            checkAll(vector)(t =>
              assert(decrypt(t.key, t.nonce, t.ad, t.ciphertext ++ t.tag))(
                equalTo(Right(t.plaintext))
              )
            )
          }
        )
      )
    )
