package perun.crypto.secp256k1

import zio.*
import zio.blocking.*
import zio.stream.*
import zio.test.*
import zio.test.Assertion.*

import io.circe.{Decoder, Json, parser}
import scodec.bits.ByteVector
import org.bitcoins.crypto.CryptoUtil.{doubleSHA256, sha256}

import perun.crypto.*
import org.bitcoins.crypto.Sha256Digest

object test extends DefaultRunnableSpec:

  given Decoder[Signature] =
    Decoder[String].map(s => Signature.fromBytes(ByteVector.fromValidHex(s)))

  given Decoder[PrivateKey] = Decoder[String].map(PrivateKey.fromHex)

  given Decoder[Sha256Digest] = Decoder[String].map(Sha256Digest.apply)

  final case class Tests(valid: Vector[Test]) derives Decoder

  final case class Test(
      m: Sha256Digest,
      d: PrivateKey,
      signature: Signature
  ) derives Decoder

  val vector: Gen[Blocking, Test] =
    Gen
      .fromEffect(
        ZStream
          .fromResource("perun/crypto/secp256k1_test_vector.json")
          .transduce(ZTransducer.utf8Decode)
          .runCollect
          .flatMap(s => ZIO.fromEither(parser.parse(s.mkString)))
          .flatMap(j => ZIO.fromEither(j.as[Tests]).map(_.valid))
          .orDie
      )
      .flatMap(Gen.fromIterable(_))

  val spec =
    suite("crypto")(
      suite("secp256k1")(
        testM("sign") {
          checkM(vector)(t =>
            assertM(signMessage(t.d, t.m))(equalTo(t.signature))
              .provideCustomLayer(native)
          )
        }
        // testM("verify")
      )
    )
