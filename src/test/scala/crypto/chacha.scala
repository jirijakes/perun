package perun.crypto.chacha

import scodec.bits.ByteVector
import zio.ZIO
import zio.json.*
import zio.stream.*
import zio.test.Assertion.*
import zio.test.*

object testSuite:

  given JsonDecoder[ByteVector] = JsonDecoder[String].map(s =>
    ByteVector.fromHex(s).getOrElse(ByteVector(s.getBytes))
  )

  final case class Test(
      @jsonField("KEY") key: ByteVector,
      @jsonField("NONCE") nonce: ByteVector,
      @jsonField("IN") plaintext: ByteVector,
      @jsonField("AD") ad: ByteVector,
      @jsonField("CT") ciphertext: ByteVector,
      @jsonField("TAG") tag: ByteVector
  )

  given JsonDecoder[Test] = DeriveJsonDecoder.gen[Test]

  // Thanks to https://github.com/calvinmetcalf/chacha20poly1305/blob/master/test/fixtures.json
  val vector =
    Gen
      .fromZIO(
        ZStream
          .fromResource("perun/crypto/chacha_test_vector.json")
          .via(ZPipeline.utf8Decode)
          .runCollect
          .flatMap(s =>
            ZIO
              .fromEither(s.mkString.fromJson[Vector[Test]])
              .mapError(m => new Exception(m))
          )
          .orDie
      )
      .flatMap(Gen.fromIterable(_))

  val spec =
    suite("chacha")(
      suite("ChaCha20-Poly1305")(
        test("encrypt") {
          checkAll(vector)(t =>
            assert(encrypt(t.key, t.nonce, t.ad, t.plaintext))(
              equalTo(t.ciphertext ++ t.tag)
            )
          )
        },
        test("decrypt") {
          checkAll(vector)(t =>
            assert(decrypt(t.key, t.nonce, t.ad, t.ciphertext ++ t.tag))(
              equalTo(Right(t.plaintext))
            )
          )
        }
      )
    )
