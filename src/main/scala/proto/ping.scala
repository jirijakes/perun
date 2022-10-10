package perun.proto.ping

import scodec.*
import scodec.bits.ByteVector
import scodec.codecs.*
import zio.*
import zio.Clock.*
import zio.Console.*
import zio.Duration.*
import zio.stream.*

import perun.proto.Message
import perun.proto.bolt.bolt.Response

/** `ping` message that serves two purposes: keep TCP connections alive
  * and obfuscate traffic. A response [[Pong]] is expected upon sending
  * a ping.
  *
  * @param num number of bytes requested in pong
  * @param len number of bytes in this ping
  * @see https://github.com/lightningnetwork/lightning-rfc/blob/master/01-messaging.md#the-ping-and-pong-messages
  */
final case class Ping(num: Int, len: Long)

/** `pong` message is a response to [[Ping]]. It contains as many bytes in `len`
  * as was requested by corresponding ping.
  *
  * @param len number of bytes in this pong
  * @see https://github.com/lightningnetwork/lightning-rfc/blob/master/01-messaging.md#the-ping-and-pong-messages
  */
final case class Pong(len: Long)

/** Codec for [[Ping]] message.
  *
  * ```text
  * [u16:num_pong_bytes]
  * [u16:byteslen]
  * [byteslen*byte:ignored]
  * ```
  */
val ping: Codec[Ping] =
  (("num_pong_bytes" | uint16) ::
    ("ignored" | variableSizeBytes("byteslen" | uint16, bytes)))
    .xmap(
      (n, b) => Ping(n, b.length),
      p => (p.num, ByteVector.fill(p.len)(0))
    )

/** Codec for [[Pong]] message.
  *
  * ```text
  * [u16:byteslen]
  * [byteslen*byte:ignored]
  * ```
  */
val pong: Codec[Pong] =
  ("ignored" | variableSizeBytes("byteslen" | uint16, bytes))
    .xmap(
      b => Pong(b.length),
      p => ByteVector.fill(p.len)(0)
    )

// TODO: Add generating errors
def receiveMessage(p: Ping): ZIO[Any, Nothing, Response] =
  ZIO.succeed(
    receivePing(p)
      .fold(Response.Ignore)(p => Response.Send(Message.Pong(p)))
  )

// TODO: measure ping rate (SHOULD fail the channels if it has received significantly in excess of one ping per 30 seconds.)
def receivePing(p: Ping): Option[Pong] =
  if p.num < 65532 then Some(Pong(p.num)) else None

/** Start sending pings in 5-minute interval and receiving corresponding pongs.
  *
  * @param hin incoming message hub
  * @param hout outgoing message hub
  */
def schedule(
    hin: Hub[Message],
    hout: Hub[Message]
): ZIO[Clock & Console, Nothing, Unit] =
  sendPing(hin, hout).repeat(Schedule.spaced(5.minutes)).unit

/** Send a ping using `hout` message hub and wait for responding pong on `hin`.
  *
  * @param hin incoming message hub
  * @param hout outgoing message hub
  */
def sendPing(
    hin: Hub[Message],
    hout: Hub[Message]
): ZIO[Clock & Console, Nothing, Unit] =
  def waitPong(ping: Ping) = ZStream
    .fromHub(hin)
    .collect { case Message.Pong(p) => p }
    .timeout(10.seconds)
    .runHead
    .flatMap {
      case Some(pong) => receivePong(ping, pong)
      case None       => printLine("NO PONG")
    }

  val ping = Ping(128, 256)
  waitPong(ping).fork *>
    // Don't know why I have to. Without waiting, first pong is not received
    ZIO.sleep(100.milliseconds) *>
    hout.publish(Message.Ping(ping)).unit

/** Process a received ping and check whether it is correect.
  *
  * @param origin ping sent by the node
  * @param received pong
  */
// TODO: Change putStrLn into failing channel
def receivePong(ping: Ping, pong: Pong): ZIO[Console, Nothing, Unit] =
  if ping.num != pong.len then
    printLine(
      s"Pong did not contain ${ping.num} bytes as expected but ${pong.len}."
    ).orDie
  else ZIO.unit
