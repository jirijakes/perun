package perun.peer

import scodec.Attempt
import scodec.codecs.uint16
import scodec.bits.ByteVector
import zio.*
import zio.stream.*

import noise.*

def start(
    in: Stream[Throwable, Byte],
    out: Sink[Throwable, Byte, Nothing, Int],
    rk: CipherState,
    sk: CipherState
) = ???

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
          case None => UIO(Chunk.empty)
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
