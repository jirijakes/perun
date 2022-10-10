package perun.test.gen

import scodec.bits.ByteVector
import zio.*
import zio.Random.*
import zio.test.*

import perun.crypto.*
import perun.crypto.keygen.*
import perun.crypto.secp256k1.Secp256k1
import perun.p2p.*
import perun.proto.blockchain.Chain
import perun.proto.codecs.*
import perun.proto.features.*
import perun.proto.gossip.ChannelAnnouncement

val anyChain: Gen[Random, Chain] =
  Gen.elements(Chain.Testnet, Chain.Mainnet, Chain.Signet)

val validKeyPair: Gen[Keygen, PrivateKey] =
  Gen.fromZIO(generateKeypair)

// val validSignature: Gen[Any, Signature] = ???

val dummySignature = Gen.const(Signature.dummy)

val validShortChannelId: Gen[Random, ShortChannelId] =
  (Gen.int(0, 25000) <*> Gen.int(0, 100) <*> Gen.int(0, 10))
    .map(ShortChannelId.apply)

val validChannelAnnouncement
    : Gen[Keygen & Secp256k1 & Random, ChannelAnnouncement] =
  (validKeyPair <*> validKeyPair <*> validKeyPair <*> validKeyPair)
    .flatMap { (node1, node2, btc1, btc2) =>
      val ca = for
        sigdum <- dummySignature
        chain <- anyChain
        shortChannelId <- validShortChannelId
        nodeId1 = NodeId.fromPublicKey(node1.publicKey)
        nodeId2 = NodeId.fromPublicKey(node2.publicKey)
        bitcoinKey1 = NodeId.fromPublicKey(btc1.publicKey)
        bitcoinKey2 = NodeId.fromPublicKey(btc2.publicKey)
      yield ChannelAnnouncement(
        sigdum,
        sigdum,
        sigdum,
        sigdum,
        Features(ByteVector.fill[Byte](8)(0)),
        chain,
        shortChannelId,
        nodeId1,
        nodeId2,
        bitcoinKey1,
        bitcoinKey2,
        ByteVector.empty
      )

      ca.mapZIO(a =>
        for
          sig1 <- a.signature(node1).orDieWith(_ => new Exception(""))
          sig2 <- a.signature(node2).orDieWith(_ => new Exception(""))
          sig3 <- a.signature(btc1).orDieWith(_ => new Exception(""))
          sig4 <- a.signature(btc2).orDieWith(_ => new Exception(""))
        yield a.signed(sig1, sig2, sig3, sig4)
      )
    }
