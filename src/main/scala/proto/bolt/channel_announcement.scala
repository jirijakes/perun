package perun.proto.bolt.channelAnnouncement

import org.bitcoins.core.protocol.script.MultiSignatureScriptPubKey
import org.bitcoins.crypto.CryptoUtil.{doubleSHA256, sha256}
import org.typelevel.paiges.Doc.*
import scodec.bits.ByteVector
import zio.*
import zio.prelude.*

import perun.crypto.*
import perun.crypto.secp256k1.*
import perun.proto.bolt.bolt.*
import perun.proto.bolt.doc.*
import perun.proto.gossip.ChannelAnnouncement
import perun.db.p2p.*
import perun.p2p.*
import perun.net.rpc.*

// <<Channel announcement signatures>>
val validateSignatures: Val[Has[Secp256k1], Nothing, ChannelAnnouncement] =
  validate(
    ctx => {
      val hash = doubleSHA256(ctx.bytes.drop(2 + 256))
      val ann = ctx.message
      predicateM(
        (
          verifySignature(ann.nodeSignature1, hash, ann.nodeId1),
          verifySignature(ann.nodeSignature2, hash, ann.nodeId2),
          verifySignature(ann.bitcoinSignature1, hash, ann.bitcoinKey1),
          verifySignature(ann.bitcoinSignature2, hash, ann.bitcoinKey2)
        ).tupleN
      )(_ && _ && _ && _, ann, failConnection("Signatures are invalid."))
    },
    text("if") & field("bitcoin_signature_1") + comma & field(
      "bitcoin_signature_2"
    ) + comma & field("node_signature_1") & text("OR") & field(
      "node_signature_2"
    ) & split("are invalid OR NOT correct:") / text(
      "SHOULD fail the connection."
    ).indent(3)
  )

// <<Channel announcement chain hash>>
val validateChain: Val[Any, Nothing, ChannelAnnouncement] =
  validate(
    ctx =>
      predicate(
        ctx.message.chain == ctx.conf.chain,
        ctx.message,
        ignore("Announcement was not meant for this chain.")
      ),
    split("if the specified") & field("chain_hash") & split(
      "is unknown to the receiver:"
    ) / text("MUST ignore the message.").indent(3)
  )

// <<Channel announcement tx output>>
val validateTxOutput: Val[Has[Rpc], Throwable, ChannelAnnouncement] =
  validate(
    ctx =>
      predicateM(txout(ctx.message.shortChannelId))(
        _.exists { out =>
          val multisig = MultiSignatureScriptPubKey(
            2,
            List(
              ctx.message.bitcoinKey1.nodeIdAsPublicKey.asECPublicKey,
              ctx.message.bitcoinKey2.nodeIdAsPublicKey.asECPublicKey
            ).sortBy(_.hex)
          )
          // TODO: can this be done more elegantly?
          out.scriptPubKey.hex == "0020" + sha256(multisig.asmBytes).hex
        },
        ctx.message,
        ignore(
          "Short channel ID does not refer to a valid unspent P2WSH transaction."
        )
      ),
    split("if the") & field("short_channel_id") + split(
      "'s output does NOT correspond to a P2WSH (using"
    ) & field("bitcoin_key_1") & text("and") & field("bitcoin_key_2") + split(
      ", as specified in BOLT #3) OR the output is spent:"
    ) / text("MUST ignore the message.").indent(3)
  )

// <<Channel announcement blacklisted node>>
val validateNodes: Val[Has[P2P], Throwable, ChannelAnnouncement] =
  validate(
    ctx =>
      predicateM(
        findNode(ctx.message.nodeId1) <&> findNode(ctx.message.nodeId2)
      )(
        _.mapN(_.blacklisted || _.blacklisted).forall(_ == false),
        ctx.message,
        ignore("One of nodes is blacklisted.")
      ),
    text("if") & field("node_id_1") & text("OR") & field("node_id_2") & split(
      "are blacklisted:"
    ) / text("SHOULD ignore the message.").indent(3)
  )

// <<Channel announcement previous announcement>>
val validatePreviousAnnouncement
    : Val[Has[P2P], Throwable, ChannelAnnouncement] =
  validate(
    ctx =>
      predicateMF(findChannel(ctx.message.shortChannelId))(
        _.forall(c =>
          c.node1 == ctx.message.nodeId1 && c.node2 == ctx.message.nodeId2
        ),
        ctx.message,
        _.fold(
          blacklist(
            "Nodes broadcast same short channel ID with different nodes.",
            ctx.message.nodeId1,
            ctx.message.nodeId2
          )
        )(c =>
          blacklist(
            "Nodes broadcast same short channel ID with different nodes.",
            ctx.message.nodeId1,
            ctx.message.nodeId2,
            c.node1,
            c.node2
          )
        )
      ),
    split("if it has previously received a valid") & field(
      "channel_announcement"
    ) + split(
      ", for the same transaction, in the same block, but for a different"
    ) & field("node_id_1") & text("or") & field("node_id_2") + char(
      ':'
    ) / (split("SHOULD blacklist the previous message's") & field(
      "node_id_1"
    ) & field("and") & field("node_id_2") + split(", as well as this") & field(
      "node_id_1"
    ) & text("and") & field("node_id_2") & split(
      "AND forget any channels connected to them."
    )).indent(3)
  )

val validation: Bolt[Has[
  Secp256k1
] & Has[Rpc] & Has[P2P], Throwable, ChannelAnnouncement] =
  bolt("#7", "Channel announcement")(
    validateChain,
    validateTxOutput,
    validateSignatures,
    validateNodes,
    validatePreviousAnnouncement
  )
