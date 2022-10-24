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

  val initMessage = Init(
    features = Flags(
      Feature.DataLossProtect,
      Feature.VarOnionOptin,
      Feature.GossipQueries,
      Feature.GossipQueriesEx,
      Feature.UpfrontShutdownScript,
      Feature.ZeroConf
    ),
    networks = Some(List(Chain.Regtest)),
    remoteAddress = None
  )

  val spec =
    suite("init")(
      test("decode") {
        assertTrue(
          init.decode(
            hex"00022100000708a000080269a2012006226e46111a0b59caaf126043eb5bbf28c34f3a5e332a1fc7b2b73cf188910f".toBitVector
          ) ==
            Attempt.successful(DecodeResult(initMessage, BitVector.empty))
        )
      },
      test("encode") {
        val expected =
          hex"0000000704000000000551012006226e46111a0b59caaf126043eb5bbf28c34f3a5e332a1fc7b2b73cf188910f".toBitVector

        assertTrue(init.encode(initMessage) == Attempt.successful(expected))
      }
    )
