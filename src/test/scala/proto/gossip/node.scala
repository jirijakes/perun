package perun.proto.gossip

import scodec.bits.ByteVector
import zio.*
import zio.test.Assertion.*
import zio.test.*

import perun.crypto.*
import perun.crypto.keygen.liveKeygen
import perun.crypto.secp256k1.native
import perun.test.gen.*

object NodeTest extends ZIOSpecDefault:

  val spec =
    suite("aaa")(
      test("sign") {
        check(validChannelAnnouncement)(ca => assert(ca)(equalTo("")))
          .provide(liveEnvironment, native, liveKeygen)
      }
    ) @@ TestAspect.ignore
