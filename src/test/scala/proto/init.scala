package perun.proto.init

import scodec.*
import scodec.bits.*
import zio.*
import zio.test.Assertion.*
import zio.test.*

import perun.crypto.*
import perun.crypto.keygen.liveKeygen
import perun.crypto.secp256k1.native
import perun.proto.blockchain.Chain
import perun.proto.features.*

import perun.test.gen.*

object InitTest extends ZIOSpecDefault:

  val msg =
    hex"00022100000708a000080269a2012006226e46111a0b59caaf126043eb5bbf28c34f3a5e332a1fc7b2b73cf188910f".toBitVector

  val expected = Init(
    Features(hex"08a000080269a2"),
    networks = Some(List(Chain.Regtest)),
    remoteAddress = None
  )

  val spec =
    suite("init")(
      test("decode") {
        assertTrue(
          init.decode(msg) ==
            Attempt.successful(DecodeResult(expected, BitVector.empty))
        )
      },
      test("encode") {
        assertTrue(init.encode(expected) == Attempt.successful(msg))
      }
    )
