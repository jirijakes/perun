import org.bitcoins.crypto.ECPrivateKey
import scodec.bits.ByteVector
import zio.*
import zio.stream.*
import zio.test.*
import Assertion.*

import lnz.handshake
import perun.test.*
import _root_.noise.*

object noise extends DefaultRunnableSpec:

  val bolt8test =
    suite("BOLT #8 test vector")(
      testM("happy scenario") {
        val chunks = List(
          "00036360e856310ce5d294e8be33fc807077dc56ac80d95d9cd4ddbd21325eff73f70df6086551151f58b8afe6c195782c6a",
          "00b9e3a702e93e3a9948c2ed6e5fd7590a6e1c3a0344cfc9d5b57357049aa22355361aa02e55a8fc28fef5bd6d71ad0c38228dc68b1c466263b47fdf31e560e139ba010101"
        ).map(s => Chunk.fromArray(ByteVector.fromValidHex(s).toArray))

        val in = Stream.fromChunks(chunks*)

        val out = ZSink.foreachChunk[Any, Throwable, Byte](_ => ZIO.unit).as(1)

        val keygen = perun.crypto.keygen.repeat(
          "2222222222222222222222222222222222222222222222222222222222222222"
        )
        val responder =
          HandshakeState.responder(
            ECPrivateKey(
              "2121212121212121212121212121212121212121212121212121212121212121"
            )
          )

        val expected = (
          lnz.Peer(
            cipherState(
              "919219dbb2920afa8db80f9a51787a840bcf111ed8d588caf9ab4be716e42b01",
              "969ab31b4d288cedf6218839b27a3e2140827047f2c0f01bf5c04435d43511a9",
            ),
            cipherState(
              "919219dbb2920afa8db80f9a51787a840bcf111ed8d588caf9ab4be716e42b01",
              "bb9020b8965f4df047e07f955f3c4b88418984aadc5cdb35096b9ea8fa5c3442",
            )
          ),
          ByteVector(1, 1, 1)
        )

        assertM(handshake(responder, in, out).provideCustomLayer(keygen))(
          equalTo(expected)
        )
      }
    )

  val spec =
    suite("noise")(
      suite("handshake")(
        suite("responder")(
          bolt8test
        )
      )
    )
