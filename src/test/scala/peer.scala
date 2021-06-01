package perun.peer

import noise.*
import scodec.bits.ByteVector
import zio.*
import zio.stream.*
import zio.test.Assertion.*
import zio.test.*

import perun.test.*

object peer extends DefaultRunnableSpec:

  val spec =
    suite("peer")(
      suite("encrypt stream")(
        testM("BOLT #8 message encryption test") {
          val cip = cipherState(
            "919219dbb2920afa8db80f9a51787a840bcf111ed8d588caf9ab4be716e42b01",
            "969ab31b4d288cedf6218839b27a3e2140827047f2c0f01bf5c04435d43511a9"
          )

          val accept = Set(0L, 1, 500, 501, 1000, 1001)

          val in = Stream.fromChunks(byteChunks("68656c6c6f")).forever

          val expected = byteChunks(
            "cf2b30ddf0cf3f80e7c35a6e6730b59fe802473180f396d88a8fb0db8cbcf25d2f214cf9ea1d95",
            "72887022101f0b6753e0c7de21657d35a4cb2a1f5cde2650528bbc8f837d0f0d7ad833b1a256a1",
            "178cb9d7387190fa34db9c2d50027d21793c9bc2d40b1e14dcf30ebeeeb220f48364f7a4c68bf8",
            "1b186c57d44eb6de4c057c49940d79bb838a145cb528d6e8fd26dbe50a60ca2c104b56b60e45bd",
            "4a2f3cc3b5e78ddb83dcb426d9863d9d9a723b0337c89dd0b005d89f8d3c05c52b76b29b740f09",
            "2ecd8c8a5629d0d02ab457a0fdd0f7b90a192cd46be5ecb6ca570bfc5e268338b1a16cf4ef2d36"
          )

          val stream =
            in.transduce(perun.peer.encrypt(cip))
              .mapChunks(c => Chunk(ByteVector.view(c.toArray)))
              .take(1002)
              .zipWithIndex
              .collect {
                case (b, i) if accept(i) => b
              }
              .run(ZSink.collectAll)

          assertM(stream)(equalTo(expected))
        }
      ),
      suite("decrypt stream")(
        testM("BOLT #8 message encryption test (roundtrip)") {
          val cip = cipherState(
            "919219dbb2920afa8db80f9a51787a840bcf111ed8d588caf9ab4be716e42b01",
            "969ab31b4d288cedf6218839b27a3e2140827047f2c0f01bf5c04435d43511a9"
          )
          val in =
            Stream.fromChunks(byteChunks("68656c6c6f")).forever.take(2000)
          val roundtrip = perun.peer.encrypt(cip) >>> perun.peer.decrypt(cip)
          val stream = in.transduce(roundtrip).run(ZSink.collectAll)
          val expected = ByteVector.fromValidHex("68656c6c6f")

          assertM(stream)(forall(equalTo(expected)))
        }
      )
    )
