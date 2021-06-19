package perun.proto.gossip

import scodec.bits.ByteVector
import zio.*
import zio.test.Assertion.*
import zio.test.*
import zio.test.environment.*

import perun.crypto.*
import perun.crypto.keygen.liveKeygen
import perun.crypto.secp256k1.native
import perun.test.gen.*

object NodeTest extends DefaultRunnableSpec:

  val spec =
    suite("aaa")(
      testM("sign") {
        checkM(validChannelAnnouncement)(ca => assertM(UIO(ca))(equalTo("")))
          .provideLayer(native ++ liveKeygen ++ testEnvironment)
      }
    )
