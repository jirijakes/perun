package perun.proto.bolt.validate

import org.bitcoins.core.protocol.script.MultiSignatureScriptPubKey
import org.bitcoins.crypto.CryptoUtil.{doubleSHA256, sha256}
import scodec.bits.ByteVector
import zio.*
import zio.stream.*

import perun.crypto.secp256k1.*
import perun.proto.*
import perun.proto.codecs.*

enum Invalid:
  case Signature(m: Message)
  case UnknownChain
  case TxOutputNotUnspent

def valid(
    b: ByteVector,
    c: Message.ChannelAnnouncement
): URIO[Has[Secp256k1], Either[Invalid, Message]] =
  val hash = doubleSHA256(b.drop(2 + 256))
  verifySignature(
    c.m.nodeSignature1.digitalSignature,
    hash,
    c.m.nodeId1.publicKey
  ).zipWith(
    verifySignature(
      c.m.nodeSignature2.digitalSignature,
      hash,
      c.m.nodeId2.publicKey
    )
  )((v1, v2) => if v1 & v2 then Right(c) else Left(Invalid.Signature(c)))

def valid(
    b: ByteVector,
    c: Message.NodeAnnouncement
): URIO[Has[Secp256k1], Either[Invalid, Message]] =
  verifySignature(
    c.m.signature.digitalSignature,
    doubleSHA256(b.drop(2 + 64)),
    c.m.nodeId.publicKey
  )
    .map(v => if v then Right(c) else Left(Invalid.Signature(c)))

def validateSignatures(
    b: ByteVector,
    m: Message
): URIO[Has[Secp256k1], Either[Invalid, Message]] =
  m match
    case m: Message.ChannelAnnouncement => valid(b, m)
    case m: Message.NodeAnnouncement    => valid(b, m)
    case _                              => UIO(Right(m))

def validateTxOutput(b: ByteVector, m: Message): Either[Invalid, Message] =
  m match
    case Message.ChannelAnnouncement(c, Some(spk)) =>
      val multisig = MultiSignatureScriptPubKey(
        2,
        List(c.bitcoinKey1.publicKey, c.bitcoinKey2.publicKey).sortBy(_.hex)
      )
      // TODO: can this be done more elegantly?
      if spk == "0020" + sha256(multisig.asmBytes).hex then Right(m)
      else Left(Invalid.TxOutputNotUnspent)
    case Message.ChannelAnnouncement(_, None) => Left(Invalid.TxOutputNotUnspent)
    case _                                    => Right(m)

def validateChain(
    conf: perun.peer.Configuration,
    m: Message
): Either[Invalid, Message] =
  m match
    // <<Channel announcement chain hash>>
    case Message.ChannelAnnouncement(c, _) if c.chain != conf.chain =>
      Left(Invalid.UnknownChain)
    case _ => Right(m)

/** Perform required steps to validate incoming message. These are described
  * in relevant BOLTs.
  *
  * @param conf configuration of connection
  * @param b complete decrypted binary message
  * @param m message decoded from b
  * @return `Left` with error description if message invalid; otherwise `Right` with original message
  */
def validate(conf: perun.peer.Configuration)(
    b: ByteVector,
    m: Message
): URIO[Has[Secp256k1], Either[Invalid, Message]] =
  validateSignatures(b, m) *> UIO(validateChain(conf, m)) *> UIO(
    validateTxOutput(b, m)
  )
