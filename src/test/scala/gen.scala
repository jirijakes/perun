package perun.test.gen

import zio.*
import zio.random.*
import zio.test.*

import perun.proto.codecs.*
import perun.proto.gossip.ChannelAnnouncement
import perun.crypto.keygen.*

val validKeyPair: Gen[Has[Keygen], PrivateKey] =
  Gen.fromEffect(generateKeypair)

val validSignature: Gen[Any, Signature] = ???

val validChannelAnnouncement: Gen[Has[Keygen] & Random, ChannelAnnouncement] =
  (validKeyPair <*> validKeyPair <*> validKeyPair <*> validKeyPair)
    .flatMap { case (((node1, node2), btc1), btc2) =>
      for
        i <- Gen.alphaChar
        nodeId1 = node1.publicKey
        nodeId2 = node2.publicKey
        bitcoinKey1 = btc1.publicKey
        bitcoinKey2 = btc2.publicKey
      yield ???
    }
