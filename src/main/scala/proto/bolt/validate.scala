package perun.proto.bolt.validate

import org.bitcoins.core.protocol.script.MultiSignatureScriptPubKey
import org.bitcoins.crypto.CryptoUtil.{doubleSHA256, sha256}
import scodec.bits.ByteVector
import zio.*
import zio.stream.*

import perun.crypto.*
import perun.crypto.secp256k1.*
import perun.proto.*
import perun.proto.codecs.nodeIdAsPublicKey

enum Invalid:
  case Signature(m: Message)
  case UnknownChain
  case TxOutputNotUnspent

type Validate = PartialFunction[Message, Either[Invalid, Message]]
type ValidateM[R] = PartialFunction[Message, ZIO[R, Invalid, Message]]

def valid(
    b: ByteVector,
    c: Message.ChannelAnnouncement
): ZIO[Has[Secp256k1], Invalid, Message] =
  val hash = doubleSHA256(b.drop(2 + 256))
  ZIO
    .collectAll(
      NonEmptyChunk(
        verifySignature(
          c.m.nodeSignature1,
          hash,
          c.m.nodeId1
        ),
        verifySignature(
          c.m.nodeSignature2,
          hash,
          c.m.nodeId2
        ),
        verifySignature(
          c.m.bitcoinSignature1,
          hash,
          c.m.bitcoinKey1
        ),
        verifySignature(
          c.m.bitcoinSignature2,
          hash,
          c.m.bitcoinKey2
        )
      )
    )
    .flatMap(vs =>
      if vs.forall(_ == true) then ZIO.succeed(c)
      else ZIO.fail(Invalid.Signature(c))
    )

def valid(
    b: ByteVector,
    c: Message.NodeAnnouncement
): ZIO[Has[Secp256k1], Invalid, Message] =
  verifySignature(
    c.m.signature,
    doubleSHA256(b.drop(2 + 64)),
    c.m.nodeId
  )
    .flatMap(v => if v then ZIO.succeed(c) else ZIO.fail(Invalid.Signature(c)))

def validateSignatures(b: ByteVector): ValidateM[Has[Secp256k1]] =
  // <<Channel announcement signatures>>
  case m: Message.ChannelAnnouncement => valid(b, m)
  // <<Node announcement signatures>>
  case m: Message.NodeAnnouncement => valid(b, m)

/** Perform validation of transaction outputs according to messages'
  * specifications.
  *
  * @param m message to validate
  * @return `Left` with details if message invalid; otherwise `Right` with original message
  */
def validateTxOutput: Validate =
  /* Channel announcement's `spk` is `None` if the relevant transaction
   * has never existed or is already spent. Only when it is `Some`, it is
   * unspent. This is the only case when we need to perform its validation.
   * <<Channel announcement tx output>>
   */
  case m @ Message.ChannelAnnouncement(c, Some(spk), _) =>
    val multisig = MultiSignatureScriptPubKey(
      2,
      List(
        c.bitcoinKey1.nodeIdAsPublicKey.asECPublicKey,
        c.bitcoinKey2.nodeIdAsPublicKey.asECPublicKey
      ).sortBy(_.hex)
    )
    // TODO: can this be done more elegantly?
    if spk == "0020" + sha256(multisig.asmBytes).hex then Right(m)
    else Left(Invalid.TxOutputNotUnspent)
  case Message.ChannelAnnouncement(_, None, _) =>
    Left(Invalid.TxOutputNotUnspent)

def validateChain(conf: perun.peer.Configuration): Validate =
  // <<Channel announcement chain hash>>
  case Message.ChannelAnnouncement(c, _, _) if c.chain != conf.chain =>
    Left(Invalid.UnknownChain)

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
  (
    validateSignatures(b).applyOrElse(m, ZIO.succeed) *>
      ZIO.fromEither(validateChain(conf).applyOrElse(m, Right(_))) *>
      ZIO.fromEither(validateTxOutput.applyOrElse(m, Right(_)))
  ).either
