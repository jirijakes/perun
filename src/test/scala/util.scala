import _root_.util.*
import zio.*
import zio.stream.*
import zio.test.Assertion.*
import zio.test.*

object util extends ZIOSpecDefault:

  def run[R, E, I, O](
      parser: ZPipeline[R, E, I, O],
      input: List[Chunk[I]]
  ): ZIO[R, E, Chunk[O]] =
    ZStream.fromChunks(input*).via(parser).runCollect

  val five = List(Chunk(1), Chunk(2), Chunk(3), Chunk(4), Chunk(5))

  val spec =
    suite("util")(
      suite("collectByLengths")(
        test("leftovers preserve chunks") {
          assertZIO(run(collectByLengths(2), five))(
            equalTo(Chunk(Chunk(1, 2), Chunk(3), Chunk(4), Chunk(5)))
          )
        },
        test("consume all") {
          assertZIO(run(collectByLengths(2, 3), five))(
            equalTo(Chunk(Chunk(1, 2), Chunk(3, 4, 5)))
          )
        }
      )
    ) // @@ TestAspect.ignore
