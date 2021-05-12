import zio.*
import zio.stream.*
import zio.test.*

import _root_.util.*

import Assertion.*

object util extends DefaultRunnableSpec:

  def run[R, E, I, O](
      parser: ZTransducer[R, E, I, O],
      input: List[Chunk[I]]
  ): ZIO[R, E, Chunk[O]] =
    ZStream.fromChunks(input*).transduce(parser).runCollect

  val five = List(Chunk(1), Chunk(2), Chunk(3), Chunk(4), Chunk(5))

  val spec =
    suite("util")(
      suite("collectByLengths")(
        testM("leftovers preserve chunks") {
          assertM(run(collectByLengths(2), five))(
            equalTo(Chunk(Chunk(1, 2), Chunk(3), Chunk(4), Chunk(5)))
          )
        },
        testM("consume all") {
          assertM(run(collectByLengths(2, 3), five))(
            equalTo(Chunk(Chunk(1, 2), Chunk(3, 4, 5)))
          )
        },
        testM("asd")(
          checkM(
            Gen.listOfBounded(1, 4)(Gen.int(1, 4)),
            Gen.listOf(Gen.chunkOfBounded(1, 5)(Gen.anyByte))
          ) { (lengths, input) =>
            println(">>> " + lengths + "  /  " + input)
            run(collectByLengths(lengths*), input).flatMap { r =>
              ZIO.effectTotal(println("@@> " + r))
            } *> assertM(ZIO.succeed(true))(isTrue)
          }
        )
      )
    )
