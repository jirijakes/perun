package perun.proto.channel

import perun.crypto.*
import perun.p2p.*
import perun.proto.blockchain.*
import perun.proto.codecs.*
import scodec.{bits as _, *}
import scodec.bits.{BitVector, ByteVector}
import scodec.codecs.*

enum ChannelFeatures:
  case NoFeatures
  case StaticRemoteKey
  case AnchorOutputsStaticRemoteKey
  case Anchors0FeeTxStaticRemoteKey

final case class ChannelType(
    features: ChannelFeatures,
    scidAlias: Boolean,
    zeroConf: Boolean
)

val channelType: Codec[ChannelType] =
  bits.xmap(
    b =>
      val flags = b.reverse
      ChannelType(
        features =
          if flags.length > 22 && flags(22) && flags(12) then
            ChannelFeatures.Anchors0FeeTxStaticRemoteKey
          else if flags.length > 20 && flags(20) && flags(12) then
            ChannelFeatures.AnchorOutputsStaticRemoteKey
          else if flags.length > 12 && flags(12) then
            ChannelFeatures.StaticRemoteKey
          else ChannelFeatures.NoFeatures,
        scidAlias = flags.length > 46 && flags(46),
        zeroConf = flags.length > 50 && flags(50)
      ),
    ct =>
      var pos = List.empty[Int]
      if ct.scidAlias then pos = pos :+ 50
      if ct.zeroConf then pos = pos :+ 46
      ct.features match
        case ChannelFeatures.NoFeatures =>
        case ChannelFeatures.StaticRemoteKey =>
          pos = pos :+ 12
        case ChannelFeatures.AnchorOutputsStaticRemoteKey =>
          pos = pos :+ 12 :+ 20
        case ChannelFeatures.Anchors0FeeTxStaticRemoteKey =>
          pos = pos :+ 12 :+ 22

      pos
        .foldLeft(BitVector.empty.padTo(pos.max))(_ set _)
        .reverse
        .toByteVector
        .toBitVector
  )

final case class OpenChannel(
    chain: Chain,
    temporaryChannelId: ChannelId,
    funding: Sat,
    push: Msat,
    dustLimit: Sat,
    maxHtlcValueInFlight: Msat,
    channelReserve: Sat,
    htlcMinimum: Msat,
    feeratePerKw: Long,
    toSelfDelay: Int,
    maxAcceptedHtlcs: Int,
    fundingPubkey: Point,
    revocationBasepoint: Point,
    paymentBasepoint: Point,
    delayedPaymentBasepoint: Point,
    htlcBasepoint: Point,
    firstPerCommitmentPoint: Point,
    channelFlags: Byte,
    upfrontShutdownScript: Option[ByteVector],
    channelType: Option[ChannelType]
)

val openChannel: Codec[OpenChannel] =
  (
    ("chain_hash" | chain) ::
      ("temporary_channel_id" | channelId) ::
      ("funding_satoshis" | sat) ::
      ("push_msat" | msat) ::
      ("dust_limit_satoshis" | sat) ::
      ("max_htlc_value_in_flight_msat" | msat) ::
      ("channel_reserve_satoshis" | sat) ::
      ("htlc_minimum_msat" | msat) ::
      ("feerate_per_kw" | uint32) ::
      ("to_self_delay" | uint16) ::
      ("max_accepted_htlcs" | uint16) ::
      ("funding_pubkey" | point) ::
      ("revocation_basepoint" | point) ::
      ("payment_basepoint" | point) ::
      ("delayed_payment_basepoint" | point) ::
      ("htlc_basepoint" | point) ::
      ("first_per_commitment_point" | point) ::
      ("channel_flags" | byte) ::
      ("open_channel_tlvs" | tlv(0L -> bytes, 1L -> channelType))
  ).as[OpenChannel]

final case class AcceptChannel(
    temporaryChannelId: ChannelId,
    dustLimit: Sat,
    maxHtlcValueInFlight: Msat,
    channelReserve: Sat,
    htlcMinimum: Msat,
    minimumDepth: Long,
    toSelfDelay: Int,
    maxAcceptedHtlcs: Int,
    fundingPubkey: Point,
    revocationPubkey: Point,
    paymentBasepoint: Point,
    delayedPaymentBasepoint: Point,
    htlcBasepoint: Point,
    firstPerCommitmentPoint: Point,
    upfrontShutdownScript: Option[ByteVector],
    channelType: Option[ByteVector]
)

val acceptChannel: Codec[AcceptChannel] = (
  ("temporary_channel_id" | channelId) ::
    ("dust_limit_satoshis" | sat) ::
    ("max_htlc_value_in_flight_msat" | msat) ::
    ("channel_reserve_satoshis" | sat) ::
    ("htlc_minimum_msat" | msat) ::
    ("minimum_depth" | uint32) ::
    ("to_self_delay" | uint16) ::
    ("max_accepted_htlcs" | uint16) ::
    ("funding_pubkey" | point) ::
    ("revocation_basepoint" | point) ::
    ("payment_basepoint" | point) ::
    ("delayed_payment_basepoint" | point) ::
    ("htlc_basepoint" | point) ::
    ("first_per_commitment_point" | point) ::
    ("accept_channel_tlvs" | tlv(0L -> bytes, 1L -> bytes))
).as[AcceptChannel]

final case class FundingCreated(
    temporaryChannelId: ChannelId,
    fundingTxid: ByteVector,
    fundingOutputIndex: Int,
    signature: Signature
)

val fundingCreated: Codec[FundingCreated] =
  (
    ("temporary_channel_id" | channelId) ::
      ("funding_txid" | bytes(32)) ::
      ("funding_output_index" | uint16) ::
      ("signature" | signature)
  ).as[FundingCreated]

final case class FundingSigned(
    channelId: ChannelId,
    signature: Signature
)

val fundingSigned: Codec[FundingSigned] =
  (
    ("channel_id" | channelId) ::
      ("signature" | signature)
  ).as[FundingSigned]

final case class FundingLocked(
    channelId: ChannelId,
    nextPerCommitmentPoint: Point
)

val fundingLocked: Codec[FundingLocked] =
  (
    ("channel_id" | channelId) ::
      ("next_per_commitment_point" | point)
  ).as[FundingLocked]

final case class Shutdown(
    channelId: ChannelId,
    scriptPubKey: ByteVector
)

val shutdown: Codec[Shutdown] =
  (
    ("channel_id" | channelId) ::
      ("scriptpubkey" | variableSizeBytes(uint16, bytes))
  ).as[Shutdown]

final case class FeeRange(min: Sat, max: Sat)

val feeRange: Codec[FeeRange] =
  (
    ("min_fee_satoshis" | sat) ::
      ("max_fee_satoshis" | sat)
  ).as[FeeRange]

final case class ClosingSigned(
    channelId: ChannelId,
    fee: Sat,
    signature: Signature,
    feeRange: Option[FeeRange]
)

val closingSigned: Codec[ClosingSigned] =
  (
    ("channel_id" | channelId) ::
      ("fee_satoshis" | sat) ::
      ("signature" | signature) ::
      ("closing_signed_tlvs" | tlv(1L -> feeRange))
  ).as[ClosingSigned]
