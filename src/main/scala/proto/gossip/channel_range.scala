package perun.proto.gossip

import com.softwaremill.quicklens.*
import scodec.*
import scodec.codecs.*
import scodec.bits.{BitVector, ByteVector}

import perun.proto.blockchain.*
import perun.proto.codecs.*
import perun.proto.UInt64.*
import perun.proto.tlv.*

final case class QueryChannelRange(
    chain: Chain,
    fistBlock: Long,
    number: Long,
    timestampsWanted: Boolean,
    checksumsWanted: Boolean
)

final case class ReplyChannelRange(
    chain: Chain,
    firstBlock: Long,
    number: Long,
    complete: Boolean,
    shortIds: Vector[ShortChannelId]
)

val tlvQueryOption: Codec[BitVector] = bigsizeBits

val tlvQueryChanelRange: Codec[BitVector] = tlvStream.exmap(
  x =>
    for
      a <- Attempt.fromOption(x.find(1), Err("ASD"))
      b <- tlvQueryOption.decode(a.toBitVector)
    yield b.value,
  y => tlvQueryOption.encode(y).map(b => TlvStream(Map((1L, b.toByteVector))))
)

/** Codec for [[QueryChannelRange]].
  *
  * ```text
  * [chain_hash:chain_hash]
  * [u32:first_blocknum]
  * [u32:number_of_blocks]
  * [query_channel_range_tlvs:tlvs]
  * ```
  */
val queryChannelRange: Codec[QueryChannelRange] =
  (
    ("chain_hash" | chain) ::
      ("first_blocknum" | uint32) ::
      ("number_of_blocks" | uint32) ::
      ("query_channel_range_tlvs" | tlvQueryChanelRange)
  ).xmapc((ch, f, n, b) => QueryChannelRange(ch, f, n, b.get(0), b.get(1))) {
    q =>
      val bits = BitVector.bits(List(q.checksumsWanted, q.timestampsWanted))
      (q.chain, q.fistBlock, q.number, bits)
  }

val replyChannelRange: Codec[ReplyChannelRange] =
  (
    ("chain_hash" | chain) ::
      ("first_blocknum" | uint32) ::
      ("number_of_blocks" | uint32) ::
      ("sync_complete" | bool(8)) ::
      ("encoded_short_ids" | variableSizeBytes("len" | uint16, encodedShortIds))
  ).as[ReplyChannelRange]
