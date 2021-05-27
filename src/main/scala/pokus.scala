import zio.*
import zio.console.*
import zio.stream.*

import scodec.bits.*

object pokus extends App:

  // def main(args: Array[String]): Unit =

  def run(args: List[String]) =
    ZStream(1, 2, 3, 4)
      .mapM(x =>
        ZIO.fromEither(
          if x % 2 == 1 then Right(x)
          else Left(s"No $x")
        )
      )
      // .tap(x => putStrLn("A> " + x))
      .either
      .tap(x => putStrLn("B> " + x))
      .foreach(x => putStrLn("C> " + x))
      .exitCode
