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

import perun.proto.{Message, Response}

def start(
    in: Stream[Throwable, Byte],
    out: Sink[Throwable, Byte, Nothing, Int],
    close: ZIO[Any, Nothing, Unit],
    rk: CipherState,
    sk: CipherState
): ZIO[Clock & Console, Nothing, Unit] =
  for
    hr <- ZHub.unbounded[perun.proto.Message]
    hw <- ZHub.unbounded[perun.proto.Message]
    _ <- ZStream
      .fromHub(hr)
      .mapM {
        case Message.Ping(p) => perun.proto.ping.receiveMessage(p)
        case Message.Init(_) => UIO(Response.Ignore)
        case Message.Pong(_) => UIO(Response.Ignore)
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
        perun.proto.Message.Init(
          perun.proto.init.Init(
            perun.proto.features.Features(hex"0x8000000000000000002822aaa2"),
            List(
              "f61eee3b63a380a477a063af32b2bbc97c9ff9f01f2c4225e973988108000000"
            )
          )
        )
      )
      .fork
    // _ <- perun.proto.ping.schedule(hr, hw).fork
    _ <- f1.join.orDie
    _ <- close
  yield ()

def decrypt(rk: CipherState): ZTransducer[Any, Nothing, Byte, ByteVector] =
  ZTransducer {
    ZRef
      .makeManaged(rk)
      .map { ref =>
        {
          case None => UIO(Chunk.empty)
          case Some(c) =>
            val (m, rest) = c.splitAt(18)
            ref.modify { cip =>
              val (length, next) =
                cip.decryptWithAd(ByteVector.empty, ByteVector.view(m.toArray))
              uint16.decodeValue(length.toBitVector) match
                case Attempt.Successful(l) =>
                  val (body, next1) = next.decryptWithAd(
                    ByteVector.empty,
                    ByteVector.view(rest.toArray)
                  )
                  (Chunk(body), next1)
                case Attempt.Failure(x) => ???
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
