package perun.test

import noise.*
import scodec.bits.ByteVector
import zio.*
import zio.stream.*

def byteStream(hex: String*): Stream[Nothing, Byte] =
  val chunks = hex.map(s => Chunk.fromArray(ByteVector.fromValidHex(s).toArray))
  Stream.fromChunks(chunks*)

def cipherState(ck: String, k: String, n: Int = 0): CipherState =
  CipherState.Running(
    ByteVector.fromValidHex(ck),
    ByteVector.fromValidHex(k),
    n
  )

def byteChunks(hex: String*): Chunk[ByteVector] =
  Chunk.fromIterable(hex.map(ByteVector.fromValidHex(_)))

def byteChunk(hex: String): Chunk[Byte] =
  Chunk.fromArray(ByteVector.fromValidHex(hex).toArray)
