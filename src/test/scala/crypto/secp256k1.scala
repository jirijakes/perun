package perun.crypto.secp256k1

import org.bitcoins.crypto.CryptoUtil.sha256
import org.bitcoins.crypto.Sha256Digest
import scodec.bits.ByteVector
import zio.*
import zio.blocking.*
import zio.json.*
import zio.stream.*
import zio.test.Assertion.*
import zio.test.*

import perun.crypto.*

object test:

  given JsonDecoder[Signature] =
    JsonDecoder[String].map(s =>
      Signature.fromBytes(ByteVector.fromValidHex(s))
    )

  given JsonDecoder[PrivateKey] = JsonDecoder[String].map(PrivateKey.fromHex)

  given JsonDecoder[Sha256Digest] = JsonDecoder[String].map(Sha256Digest.apply)

  final case class Test(
      m: Sha256Digest,
      d: PrivateKey,
      signature: Signature
  )

  given JsonDecoder[Test] = DeriveJsonDecoder.gen

  final case class Tests(valid: Vector[Test])

  given JsonDecoder[Tests] = DeriveJsonDecoder.gen

  // Thanks to https://github.com/bitcoinjs/tiny-secp256k1
  val vector: Gen[Blocking, Test] =
    Gen
      .fromEffect(
        ZStream
          .fromResource("perun/crypto/secp256k1_test_vector.json")
          .transduce(ZTransducer.utf8Decode)
          .runCollect
          .flatMap(s =>
            ZIO
              .fromEither(s.mkString.fromJson[Tests].map(_.valid))
              .mapError(m => new Exception(m))
          )
          .orDie
      )
      .flatMap(Gen.fromIterable(_))

  // TODO: Is the JSON loaded and parsed twice? Can something be done about that?
  val spec =
    suite("secp256k1")(
      testM("sign") {
        checkM(vector)(t =>
          assertM(signMessage(t.d, t.m))(equalTo(t.signature))
            .provideCustomLayer(native)
        )
      },
      testM("verify") {
        checkM(vector)(t =>
          assertM(
            verifySignature(t.signature, t.m, t.d.publicKey)
          )(isTrue).provideCustomLayer(native)
        )
      }
    )
