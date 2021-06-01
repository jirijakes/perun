package perun.proto.gossip

import scodec.bits.ByteVector
import zio.test.Assertion.*
import zio.test.*

import perun.crypto.*
import perun.crypto.secp256k1.native
import perun.proto.blockchain.Chain
import perun.proto.codecs.{NodeId, ShortChannelId}
import perun.proto.features.*

object NodeTest extends DefaultRunnableSpec:

  val spec = testM("sign") {
    PrivateKey.freshPrivateKey
      .flatMap { sec =>
        val an = ChannelAnnouncement(
          Signature.dummy,
          Signature.dummy,
          Signature.dummy,
          Signature.dummy,
          Features(ByteVector.fill[Byte](8)(0)),
          Chain.Testnet,
          ShortChannelId(1000, 0, 0),
          NodeId.fromPublicKey(sec.publicKey),
          NodeId.fromPublicKey(sec.publicKey),
          NodeId.fromPublicKey(sec.publicKey),
          NodeId.fromPublicKey(sec.publicKey),
          ByteVector.empty
        )

        an.signature(sec).map { case Some(sig) =>
          an.signed(sig, sig, sig, sig)
        }
      }
      .map { x =>
        assert(x)(equalTo("asd"))
      }
      .provideCustomLayer(native)
  }
