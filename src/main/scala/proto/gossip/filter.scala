package perun.proto.gossip

import com.softwaremill.quicklens._
import scodec.*
import scodec.bits.ByteVector
import scodec.codecs.*

import perun.peer.State
import perun.proto.blockchain.*

/**
  */
final case class GossipTimestampFilter(
    chain: Chain,
    firstTimestamp: Long,
    timestampRange: Long
)

/** Codec for [[GossipTimestampFilter]].
  *
  * ```text
  * [chain_hash:chain_hash]
  * [u32:first_timestamp]
  * [u32:timestamp_range]
  * ```
  */
val gossipTimestampFilter: Codec[GossipTimestampFilter] =
  (
    ("chain_hash" | chain) ::
      ("first_timestamp" | uint32) ::
      ("timestamp_range" | uint32)
  ).as[GossipTimestampFilter]

def receiveMessage(m: GossipTimestampFilter, state: State): State =
  modify(state)(_.gossipFilter).setTo(Some(m))
