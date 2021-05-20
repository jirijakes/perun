package perun.peer

import scodec.*
import scodec.codecs.uint16
import scodec.bits.*
import scodec.bits.hex
import zio.*
import zio.clock.Clock
import zio.console.*
import zio.duration.*
import zio.stream.*

import noise.*

import perun.db.*
import perun.proto.{Message, Response}
import perun.proto.blockchain.Chain
import perun.proto.features.Features
import perun.proto.init.Init
import perun.proto.gossip.{
  GossipTimestampFilter,
  receiveMessage as receiveGTF,
  QueryChannelRange
}
import scala.annotation.tailrec

final case class State(
    gossipFilter: Option[GossipTimestampFilter]
)

def start(
    in: Stream[Throwable, Byte],
    out: Sink[Throwable, Byte, Nothing, Int],
    close: ZIO[Any, Nothing, Unit],
    rk: CipherState,
    sk: CipherState
): ZIO[Store & Clock & Console, Nothing, Unit] =
  for
    state <- Ref.make(State(None))
    // _ <- execute("CREATE TABLE prd (id INT)").orDie
    hr <- ZHub.unbounded[perun.proto.Message]
    hw <- ZHub.unbounded[perun.proto.Message]
    _ <- ZStream
      .fromHub(hr)
      .mapM {
        case Message.Ping(p) => perun.proto.ping.receiveMessage(p)
        case Message.GossipTimestampFilter(f) =>
          state.update(receiveGTF(f, _)).as(Response.Ignore)
        case Message.Init(_)              => UIO(Response.Ignore)
        case Message.Pong(_)              => UIO(Response.Ignore)
        case _: Message.ReplyChannelRange => UIO(Response.Ignore)
      }
      .foreach {
        case Response.Ignore  => ZIO.unit
        case Response.Send(m) => hw.publish(m)
      }
      .fork
    f1 <- in
      .transduce(decrypt(rk))
      .map(perun.proto.decode)
      .foreach {
        case Right(m) => putStrLn(s"Received: $m") *> hr.publish(m)
        case Left(e)  => putStrLn("Error: " + e)
      }
      .fork
    f3 <- ZStream
      .fromHub(hw)
      .tap(i => putStrLn(s"Sending: $i"))
      .map(x => perun.proto.encode(x))
      .transduce(encrypt(sk))
      .run(out)
      .fork
    _ <- ZIO.sleep(1.second) *> hw
      .publish(
        Message.Init(
          Init(Features(hex"0x8000000000000000002822aaa2"), List(Chain.Testnet))
        )
      )
      .fork
    // _ <- ZIO.sleep(2.second) *> hw
    // .publish(
    // Message.QueryChanellRange(
    // QueryChannelRange(Chain.Testnet, 1905770, 1000, false, false)
    // )
    // )
    // .fork
    _ <- ZIO.sleep(3.second) *> hw
      .publish(
        Message.GossipTimestampFilter(
          GossipTimestampFilter(Chain.Testnet, 1621345431, 2000000000)
        )
      )
      .fork
    // _ <- perun.proto.ping.schedule(hr, hw).fork
    _ <- f1.join.mapError(s => new Exception(s.toString)).orDie
    _ <- close
  yield ()

// final case class Decrypt(rk: CipherState, leftover: Option[ByteVector])

enum DecodedOne:
  case DecryptError(msg: String)
  case DecodeError(msg: String)
  case Done(plaintext: ByteVector, newState: CipherState)
  case Leftover(
      plaintext: ByteVector,
      leftover: Chunk[Byte],
      newState: CipherState
  )
  case Demand(part: Chunk[Byte], state: CipherState)

/** Attempt to decrypt and decode `bytes` with cipher state `cip`. In case of decryption
  * error (e. g. wrong tag), returns a relevant type of error as `Left`. Otherwise,
  * returns returns `Right` with [[DecryptResult]].
  *
  * @param cip initial cipher state
  * @param bytes ciphertext
  */
def decodeOne(cip: CipherState, bytes: Chunk[Byte]): DecodedOne =
  // 1. Read exactly 18 bytes from the network buffer.
  // 2. Let the encrypted length prefix be known as lc.
  if bytes.length < 18 then DecodedOne.Demand(bytes, cip)
  else
    val (lc, rest) = bytes.splitAt(18)

    // 3. Decrypt lc […] to obtain the size of the encrypted packet l
    //   -  A zero-length byte slice is to be passed as the AD (associated data).
    //   -  The nonce rn MUST be incremented after this step.
    cip.decryptWithAd0(ByteVector.view(lc.toArray)) match
      case Left(x) =>
        DecodedOne.DecryptError(x.toString)
      case Right(lb, next) =>
        uint16.decodeValue(lb.toBitVector) match
          case Attempt.Failure(err) =>
            DecodedOne.DecodeError(err.message)
          case Attempt.Successful(l) =>
            // 4. Read exactly l+16 bytes from the network buffer, and let the bytes be known as c.
            val (c, nextMsg) = rest.splitAt(l + 16)
            if c.length < l + 16 then DecodedOne.Demand(bytes, cip)
            else
              // 5. Decrypt c […] to obtain decrypted plaintext packet p.
              //   - The nonce rn MUST be incremented after this step.
              next.decryptWithAd0(ByteVector.view(c.toArray)) match
                case Left(x) =>
                  DecodedOne.DecryptError(x.toString)
                case Right(p, nextState) =>
                  if nextMsg.isEmpty then DecodedOne.Done(p, nextState)
                  else DecodedOne.Leftover(p, nextMsg, nextState)

enum Decoded:
  case DecryptError(msg: String)
  case DecodeError(msg: String)
  case Done(plaintext: Chunk[ByteVector], newState: CipherState)
  case Demand(
      complete: Chunk[ByteVector],
      part: Chunk[Byte],
      state: CipherState
  )

def decodeAll(cip: CipherState, bs: Chunk[Byte]): Decoded =
  @tailrec def go(
      cip: CipherState,
      bytes: Chunk[Byte],
      agg: Chunk[ByteVector]
  ): Decoded =
    decodeOne(cip, bytes) match
      case DecodedOne.DecryptError(m)        => Decoded.DecryptError(m)
      case DecodedOne.DecodeError(m)         => Decoded.DecodeError(m)
      case DecodedOne.Done(msg, s)           => Decoded.Done(agg :+ msg, s)
      case DecodedOne.Demand(p, s)           => Decoded.Demand(agg, p, s)
      case DecodedOne.Leftover(msg, left, s) => go(s, left, agg :+ msg)
  // println("0 >>>>>>>> " + bs.size)
  go(cip, bs, Chunk.empty)

final case class DecryptState(
    cip: CipherState,
    demand: Option[Chunk[Byte]]
)

def decrypt(rk: CipherState): ZTransducer[Any, String, Byte, ByteVector] =
  ZTransducer {
    ZRefM
      .makeManaged(DecryptState(rk, None))
      .map { ref =>
        {
          case None =>
            ref.get.map(
              _.demand.fold(Chunk.empty)(p => Chunk(ByteVector.view(p.toArray)))
            )
          case Some(bytes) =>
            ref.modify { case DecryptState(cip, dem) =>
              decodeAll(cip, dem.fold(bytes)(_ ++ bytes)) match
                case x @ Decoded.Done(cs, st) =>
                  UIO((cs, DecryptState(st, None)))
                case x @ Decoded.Demand(agg, part, st) =>
                  UIO((agg, DecryptState(st, Some(part))))
                case Decoded.DecryptError(x) =>
                  ZIO.fail(s"Decrypt Error: $x")
                case Decoded.DecodeError(x) =>
                  ZIO.fail(s"Decode Error: $x")
            }
        }
      }
  }

def encrypt(sk: CipherState): ZTransducer[Any, Nothing, ByteVector, Byte] =
  ZTransducer {
    ZRef
      .makeManaged(sk)
      .map { ref =>
        {
          case None                 => UIO(Chunk.empty)
          case Some(c) if c.isEmpty => UIO(Chunk.empty)
          case Some(c) =>
            val b = c.head
            val length = uint16.encode(b.length.toInt).getOrElse(???)
            ref.modify { cip =>
              val (lengthC, next) =
                cip.encryptWithAd(ByteVector.empty, length.toByteVector)
              val (bodyC, next1) = next.encryptWithAd(ByteVector.empty, b)
              (Chunk.fromArray((lengthC ++ bodyC).toArray), next1)
            }
        }
      }
  }
