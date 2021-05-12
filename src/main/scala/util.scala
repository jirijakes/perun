package util

import zio.{Chunk, ZRef}
import zio.stream.ZTransducer

/** Emits chunks of lengths specified by `lengths` and then the rest of the stream.
  *
  * After all the chunks of requested lengths have been emitted, the rest of
  * streams will preserve original chunks. The only exception is the first chunk
  * following requested chunks which may be of different length.
  * 
  * If not enough elements are available, the stream will wait for new elements
  * or end of stream when it will not emit any new chunks as the demand could
  * not be satisfied.
  *
  * @example
  *
  * ```scala
  * val s: UStream[Chunk[Int]] =
  *   ZStream
  *     .iterate(1)(_ + 1)
  *     .take(10)
  *     .chunkN(2)
  *     .transduce(collectByLengths(1, 3, 2))
  * ```
  *
  * Emits the following chunks:
  *
  *   - `Chunk(1)`     (length 1)
  *   - `Chunk(2,3,4)` (length 3)
  *   - `Chunk(5,6)`   (length 2)
  *   - `Chunk(7,8)`   (remaining chunk because chunkN = 2)
  *   - `Chunk(9,10)`  (remaining chunk because chunkN = 2)
  *
  * @param lengths vararg specifying requested lenghts of chunks
  */
def collectByLengths[T](
    lengths: Int*
): ZTransducer[Any, Nothing, T, Chunk[T]] =
  ZTransducer {
    ZRef
      .makeManaged((lengths.toList, Chunk[T]()))
      .map { st =>
        {
          case None => st.modify( (_, s) => (Chunk.empty, (Nil, s)))
          case Some(c) =>
            st.modify {
              case (l @ (h :: t), s) =>
                val (c1, c2) = (s ++ c).splitAt(h)
                if c1.length < h then (Chunk.empty, (l, c1))
                else if t.isEmpty && c2.nonEmpty then
                  (Chunk(c1, c2), (Nil, Chunk.empty))
                else (Chunk(c1), (t, c2))
              case (Nil, s) => (Chunk(c), (Nil, Chunk.empty))
            }
        }
      }
  }
