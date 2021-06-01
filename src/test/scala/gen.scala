package perun.test.gen

import scodec.bits.ByteVector
import zio.*
import zio.random.*
import zio.test.*

import perun.crypto.*
import perun.crypto.keygen.*
import perun.proto.blockchain.Chain
import perun.proto.codecs.*
import perun.proto.gossip.ChannelAnnouncement

val anyChain: Gen[Random, Chain] =
  Gen.elements(Chain.Testnet, Chain.Mainnet, Chain.Signet)

val validKeyPair: Gen[Has[Keygen], PrivateKey] =
  Gen.fromEffect(generateKeypair)

val validSignature: Gen[Any, Signature] = ???

val dummySignature = Gen.const(Signature.dummy)

val validShortChannelId: Gen[Random, ShortChannelId] =
  Gen.zipN(Gen.int(0, 25000), Gen.int(0, 100), Gen.int(0, 10))(
    ShortChannelId.apply
  )

val validChannelAnnouncement: Gen[Has[Keygen] & Random, ChannelAnnouncement] =
  (validKeyPair <*> validKeyPair <*> validKeyPair <*> validKeyPair)
    .flatMap { case (((node1, node2), btc1), btc2) =>
      for
        sig <- dummySignature
        chain <- anyChain
        shortChannelId <- validShortChannelId
        nodeId1 = NodeId.fromPublicKey(node1.publicKey)
        nodeId2 = NodeId.fromPublicKey(node2.publicKey)
        bitcoinKey1 = NodeId.fromPublicKey(btc1.publicKey)
        bitcoinKey2 = NodeId.fromPublicKey(btc2.publicKey)
      yield ChannelAnnouncement(
        sig,
        sig,
        sig,
        sig,
        ???,
        chain,
        shortChannelId,
        nodeId1,
        nodeId2,
        bitcoinKey1,
        bitcoinKey2,
        ByteVector.empty
      )
    }
