package perun.test.gen

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

val validShortChannelId: Gen[Random, ShortChannelId] =
  Gen.zipN(Gen.int(0, 25000), Gen.int(0, 100), Gen.int(0, 10))(
    ShortChannelId.apply
  )

val validChannelAnnouncement: Gen[Has[Keygen] & Random, ChannelAnnouncement] =
  (validKeyPair <*> validKeyPair <*> validKeyPair <*> validKeyPair)
    .flatMap { case (((node1, node2), btc1), btc2) =>
      for
        chain <- anyChain
        shortChannelId <- validShortChannelId
        nodeId1 = node1.publicKey
        nodeId2 = node2.publicKey
        bitcoinKey1 = btc1.publicKey
        bitcoinKey2 = btc2.publicKey
      yield ???
    }
