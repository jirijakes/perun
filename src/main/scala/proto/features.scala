package perun.proto.features

import scodec.*
import scodec.bits.{BitVector, ByteVector}
import scodec.codecs.*
import scala.runtime.EnumValue

opaque type Features = ByteVector

// TODO: remove
object Features:
  def apply(b: ByteVector): Features = b

val features: Codec[Features] = variableSizeBytes("flen" | uint16, bytes)

trait F:
  def name: String
  def description: String
  def bit: Int

enum Feature(
    override val bit: Int,
    override val description: String,
    override val name: String
) extends F:
// format: off
  case DataLossProtect       extends Feature(0,  "option_data_loss_protect", "Requires or supports extra channel_reestablish fields")
  case InitialRoutingSync    extends Feature(3,  "initial_routing_sync", "Sending node needs a complete routing information dump")
  case UpfrontShutdownScript extends Feature(4,  "option_upfront_shutdown_script", "Commits to a shutdown scriptpubkey when opening channel")
  case GossipQueries         extends Feature(6,  "gossip_queries", "More sophisticated gossip control")
  case VarOnionOptin         extends Feature(8,  "var_onion_optin", "Requires/supports variable-length routing onion payloads")
  case GossipQueriesEx       extends Feature(10, "gossip_queries_ex", "Gossip queries can include additional information")
  case ZeroConf              extends Feature(50, "option_zeroconf", "Understands zeroconf channel types")
// format: on

class Flags[T <: F] private (private val set: Set[T]):
  def has(t: T): Boolean = set.contains(t)
  def set(t: T): Flags[T] = new Flags(set.incl(t))
  def flags: Set[T] = set
  override def toString(): String = s"Flags(${flags.mkString(",")})"
  override def equals(x: Any): Boolean =
    x match
      case f: Flags[T @unchecked] => f.set == this.set
      case _                      => false

  def bits: BitVector =
    // Length of new bitvector has to be aligned to 8 bytes for correct conversion to bytevector
    val len =
      val l = set.maxBy(_.bit).bit + 1
      l + 8 - (l % 8)
    set.iterator
      .map(_.bit)
      .foldLeft(BitVector.fill(len)(false))((b, i) => b.set(len - i - 1))

object Flags:
  private def fromBitsValues[T <: F](bits: BitVector, all: Array[T]): Flags[T] =
    val len = bits.length

    @annotation.tailrec
    def go(b: BitVector, i: Long, acc: Set[Long]): Set[Long] =
      if b.isEmpty then acc
      else go(b.tail, i + 1, if b.head then acc.incl(len - i) else acc)

    val isActive = go(bits, 1, Set())

    new Flags(
      all.iterator
        .filter(x =>
          isActive(x.bit) ||
            isActive(if x.bit % 2 == 0 then x.bit + 1 else x.bit - 1)
        )
        .toSet
    )

  inline def fromBits[T <: F & scala.reflect.Enum](bits: BitVector): Flags[T] =
    fromBitsValues(bits, perun.macros.enumValues[T])

  def apply[E <: F](flags: E*): Flags[E] =
    new Flags(flags.toSet)

inline def flags[E <: F & scala.reflect.Enum](
    bytes: Codec[ByteVector]
): Codec[Flags[E]] =
  bytes.xmap(
    b => Flags.fromBits[E](b.toBitVector),
    _.bits.toByteVector
  )
