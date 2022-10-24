package perun.peer

import scala.annotation.tailrec

import noise.*
import scodec.*
import scodec.bits.{hex, *}
import scodec.codecs.uint16
import org.typelevel.paiges.Document.ops.*
import zio.*
import zio.Clock
import zio.Console.*
import zio.Duration.*
import zio.stream.*

import perun.crypto.secp256k1.Secp256k1
import perun.db.p2p.*
import perun.db.store.*
import perun.net.rpc.*
import perun.proto.blockchain.Chain
import perun.proto.features.*
import perun.proto.gossip.{
  GossipTimestampFilter,
  QueryChannelRange,
  receiveMessage as receiveGTF
}
import perun.proto.init.Init
import perun.proto.Message
import perun.proto.bolt.bolt.{Invalid, Response}
import perun.proto.codecs.toBytes
import perun.proto.DecodeError

final case class State(
    gossipFilter: Option[GossipTimestampFilter]
)

final case class Configuration(
    chain: Chain
)

def start(
    conf: Configuration,
    in: Stream[Throwable, Byte],
    out: Sink[Throwable, Byte, Nothing, Int],
    close: ZIO[Any, java.io.IOException, Unit],
    rk: CipherState,
    sk: CipherState
): ZIO[
  // Has[Store] &
  Clock & Console & P2P & Secp256k1 & Rpc,
  Nothing,
  Unit
] =
  for
    state <- Ref.make(State(None))
    // _ <- execute("CREATE TABLE prd (id INT)").orDie
    hr <- Hub.unbounded[ByteVector]
    hw <- Hub.unbounded[Message]
    _ <- ZIO.scoped {
      ZStream
        .fromHub(hr)
        .map(b => (b, perun.proto.decode(b)))
        .tap(showDecodeResult)
        .collect { case (b, Right(m)) => (b, m) }
        /* This can be parallelized but attention still has to be paid to
         * order of messages. Peer will send us channel announcements first,
         * then node announcements. We should make sure that validation of
         * node announcement will not depend on when channel announcement
         * is processed. There needs to be some synchronization or retrying.
         */
        .mapZIO(perun.proto.bolt.bolt.validate(conf))
        .partitionEither(a => ZIO.succeed(a.toEither)) //validate(conf))
        .flatMap { (errs, msgs) =>
          val s1 = msgs
            .mapZIO {
              case Message.Ping(p) => perun.proto.ping.receiveMessage(p)
              case Message.GossipTimestampFilter(f) =>
                state.update(receiveGTF(f, _)).as(Response.Ignore)
              case Message.Init(_) => ZIO.succeed(Response.Ignore)
              case Message.Pong(_) => ZIO.succeed(Response.Ignore)
              case Message.ReplyChannelRange(r) =>
                ZIO.succeed(Response.Ignore)
              case Message.NodeAnnouncement(n) =>
                offerNode(n).as(Response.Ignore)
              case Message.ChannelAnnouncement(c) =>
                offerChannel(c).as(Response.Ignore)
              case Message.ChannelUpdate(c) =>
                updateChannel(c).as(Response.Ignore)
              case Message.QueryChanellRange(q) => ZIO.succeed(Response.Ignore)
            }

          val s2 = errs.map(
            _.head match
              case Invalid.Denied(_, r) => r
          )

          s1.merge(s2).foreach {
            case Response.Send(m)        => hw.publish(m).unit
            case Response.Ignore         => ZIO.unit
            case Response.FailConnection => ZIO.unit
          }
        }
    }.fork
    _ <- in.chunks
      .via(decrypt(rk))
      .flattenChunks
      .foreach(b => hr.publish(b))
      .fork
    _ <- ZStream
      .fromHub(hw)
      .tap(i => printLine(s"Sending: $i"))
      .map(x => perun.proto.encode(x))
      .via(encrypt(sk))
      .flattenChunks
      .run(out)
      .fork
    _ <- (ZIO.sleep(1.second) *> hw
      .publish(
        Message.Init(
          Init(
            Flags(Feature.GossipQueries),
            Some(List(Chain.Regtest)),
            None
          )
        )
      )).fork
    _ <- ZIO.sleep(5.second) *> hw
      .publish(
        Message.QueryChanellRange(
          QueryChannelRange(Chain.Regtest, 0, 1000, false, false)
        )
      )
      .fork
    // _ <- (ZIO.sleep(3.second) *> hw
    //   .publish(
    //     Message.GossipTimestampFilter(
    //       GossipTimestampFilter(Chain.Regtest, 0L, 4294967295L)
    //     )
    //   )).fork
    // // _ <- perun.proto.ping.schedule(hr, hw).fork
    _ <- ZIO.never
  yield ()

def showDecodeResult(bytes: ByteVector, result: Either[DecodeError, Message]) =
  import org.typelevel.paiges.Doc.*
  import org.typelevel.paiges.Style.*

  val d = result match
    case Left(DecodeError.UnknownMessage(disc)) =>
      text("   ").style(Ansi.Bg.BrightYellow) &
        text("Unknown discriminator " + disc).style(Ansi.Fg.BrightYellow) /
        text("   ").style(Ansi.Bg.BrightYellow) &
        text(bytes.toString).style(Ansi.Fg.BrightBlack)
    case Left(DecodeError.Other(e)) =>
      text("   ").style(Ansi.Bg.Red) &
        text(e.toString()).style(Ansi.Fg.BrightRed) /
        text("   ").style(Ansi.Bg.Red) &
        text(bytes.toString).style(Ansi.Fg.BrightBlack)
    case Right(m) =>
      text("   ").style(Ansi.Bg.Green) &
        text(pprint(m).render) /
        text("   ").style(Ansi.Bg.Green) &
        text(bytes.toString).style(Ansi.Fg.BrightBlack)

  Console.printLine(d.render(100))

/** Result of calling [[decryptOne]]. */
enum DecryptedOne:

  /** An error occurred during decryption. This most likely happened because of invalid HMAC tag.
    * It may be because of malicious or buggy peer's client.
    *
    * @param msg error message with more details
    */
  case DecryptError(msg: String)

  /** An error occurred during decoding. [[decryptOne]] function performs only one decoding (message length)
    * therefore the problem probably appeared there. The reason may be malicious or buggy peer's client.
    *
    * @param msg error message with more details
    */
  case DecodeError(msg: String)

  /** The ciphertext was completely and correctly decrypted and no more bytes remained.
    *
    * @param plaintext decrypted message
    * @param newState new cipher state
    */
  case Done(plaintext: ByteVector, newState: CipherState)

  /** The ciphertext was correctly decrypted but more encrypted bytes remained.
    *
    * @param plaintext decrypted message
    * @param leftover encrypted remaining bytes
    * @param newState new cipher state
    */
  case Leftover(
      plaintext: ByteVector,
      leftover: Chunk[Byte],
      newState: CipherState
  )

  /** Message length was correctly decoded however the provided ciphertext did not contain
    * enough bytes. More must be provided.
    *
    * @param part complete encrypted bytes, including message length
    * @param state original state
    */
  case Demand(part: Chunk[Byte], state: CipherState)

/** Attempt to decrypt first message from `ciphertext` with cipher state `cip`.
  *
  * The result of this attempt may be one of:
  *
  * - **Done**: All bytes decrypted
  * - **Leftover**: Some bytes decrypted but more bytes remain
  * - **Demand**: Length of message decoded but not enough bytes available for the message
  * - **DecryptError** : Could not decrypt bytes due to bad HMAC tag or similar
  * - **DecodeError**: Could not decode decrypted message message length
  *
  * @param ciphertext
  * @param cip initial cipher state
  * @return result of decryption with decrypted message, if available
  * @see [[https://github.com/lightningnetwork/lightning-rfc/blob/master/08-transport.md#receiving-and-decrypting-messages BOLT #8 Receiving and Decrypting Messages]]
  */
def decryptOne(ciphertext: Chunk[Byte], cip: CipherState): DecryptedOne =
  // 1. Read exactly 18 bytes from the network buffer.
  // 2. Let the encrypted length prefix be known as lc.
  if ciphertext.length < 18 then DecryptedOne.Demand(ciphertext, cip)
  else
    val (lc, rest) = ciphertext.splitAt(18)

    // 3. Decrypt lc […] to obtain the size of the encrypted packet l
    //   -  A zero-length byte slice is to be passed as the AD (associated data).
    //   -  The nonce rn MUST be incremented after this step.
    cip.decryptWithAd0(ByteVector.view(lc.toArray)) match
      case Left(x) =>
        DecryptedOne.DecryptError(x.toString)
      case Right(lb, next) =>
        uint16.decodeValue(lb.toBitVector) match
          case Attempt.Failure(err) =>
            DecryptedOne.DecodeError(err.message)
          case Attempt.Successful(l) =>
            // 4. Read exactly l+16 bytes from the network buffer, and let the bytes be known as c.
            val (c, nextMsg) = rest.splitAt(l + 16)
            if c.length < l + 16 then DecryptedOne.Demand(ciphertext, cip)
            else
              // 5. Decrypt c […] to obtain decrypted plaintext packet p.
              //   - The nonce rn MUST be incremented after this step.
              next.decryptWithAd0(ByteVector.view(c.toArray)) match
                case Left(x) =>
                  DecryptedOne.DecryptError(x.toString)
                case Right(p, nextState) =>
                  if nextMsg.isEmpty then DecryptedOne.Done(p, nextState)
                  else DecryptedOne.Leftover(p, nextMsg, nextState)

/** Result of calling [[decryptAll]]. */
enum Decrypted:

  /** An error occurred during decryption. This most likely happened because of invalid HMAC tag.
    * It may be because of malicious or buggy peer's client.
    *
    * @param msg error message with more details
    */
  case DecryptError(msg: String)

  /** An error occurred during decoding. [[decryptAll]] function performs only decoding of message lengths
    * therefore the problem probably appeared there. The reason may be malicious or buggy peer's client.
    *
    * @param msg error message with more details
    */
  case DecodeError(msg: String)

  /** The ciphertext was completely and correctly decrypted and no more bytes remained.
    *
    * @param plaintext decrypted messages
    * @param newState new cipher state
    */
  case Done(plaintext: Chunk[ByteVector], newState: CipherState)

  /** Some messages were correctly decrypted however the provided ciphertext did not contain
    * enough bytes for the next message. More bytes must be provided.
    *
    * @param complete completely decrypted messages
    * @param part incomplete encrypted bytes of next message, including message length
    * @param nextState next state
    */
  case Demand(
      complete: Chunk[ByteVector],
      part: Chunk[Byte],
      nextState: CipherState
  )

/** Attempt to decrypt all messages contained in `ciphertext` with cipher state `cip`.
  * It recursively calls [[decryptOne]] and collects plaintexts. Unlike [[decryptOne]],
  * this function cannot return leftover, the purpose of this function is to deal with
  * the leftovers.
  *
  * The result of this attempt may be one of:
  *
  * - **Done**: All bytes and messages decrypted
  * - **Demand**: Some messages decrypted but more encrypted bytes remain
  * - **DecryptError** : Could not decrypt bytes due to bad HMAC tag or similar
  * - **DecodeError**: Could not decode decrypted message message length
  *
  * @param ciphertext
  * @param cip initial cipher state
  * @return result of decryption with decrypted messages, if available
  */
def decryptAll(ciphertext: Chunk[Byte], cip: CipherState): Decrypted =
  @tailrec def go(
      ciphertext: Chunk[Byte],
      cip: CipherState,
      agg: Chunk[ByteVector]
  ): Decrypted =
    decryptOne(ciphertext, cip) match
      case DecryptedOne.DecryptError(m)        => Decrypted.DecryptError(m)
      case DecryptedOne.DecodeError(m)         => Decrypted.DecodeError(m)
      case DecryptedOne.Done(pln, s)           => Decrypted.Done(agg :+ pln, s)
      case DecryptedOne.Demand(part, s)        => Decrypted.Demand(agg, part, s)
      case DecryptedOne.Leftover(pln, left, s) => go(left, s, agg :+ pln)
  go(ciphertext, cip, Chunk.empty)

/** State of [[decrypt]] transducer. Besides a necessary cipher state,
  * it holds, if available, an incomplete encrypted message that is expecting
  * more bytes.
  *
  * @param cip continuosly updated cipher state
  * @param demand incomplete encrypted message, if avaible from previous step
  */
final case class DecryptState(
    cip: CipherState,
    demand: Option[Chunk[Byte]]
)

def decrypt(
    rk: CipherState
): ZPipeline[Any, String, Chunk[Byte], Chunk[ByteVector]] =
  ZPipeline.mapAccumZIO(DecryptState(rk, None)) {
    case (DecryptState(cip, dem), bytes) => {
      decryptAll(dem.fold(bytes)(_ ++ bytes), cip) match
        case Decrypted.Done(cs, st) =>
          ZIO.succeed((DecryptState(st, None), cs))
        case Decrypted.Demand(agg, part, st) =>
          ZIO.succeed((DecryptState(st, Some(part)), agg))
        case Decrypted.DecryptError(x) =>
          ZIO.fail(s"Decrypt Error: $x")
        case Decrypted.DecodeError(x) =>
          ZIO.fail(s"Decode Error: $x")

    }
  }

// TODO: Might be better to create a home-made pipeline which has out type Byte instead of Chunk[Byte].
def encrypt(sk: CipherState): ZPipeline[Any, Nothing, ByteVector, Chunk[Byte]] =
  ZPipeline
    .mapAccum(sk) { (cip, b) =>
      val length = uint16.encode(b.length.toInt).getOrElse(???)
      val (lengthC, next) =
        cip.encryptWithAd(ByteVector.empty, length.toByteVector)
      val (bodyC, next1) = next.encryptWithAd(ByteVector.empty, b)
      (next1, Chunk.fromArray((lengthC ++ bodyC).toArray))
    }
