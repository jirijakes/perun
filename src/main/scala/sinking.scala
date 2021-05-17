import zio.*
import zio.console.*
import zio.stream.*

object sinking extends App:

  trait X:
    def write(m: String): ZIO[Console, Throwable, Unit]

  val s1 = ZSink.foreach[Console, Throwable, String](putStrLn(_))

  val t1 = ZTransducer[Any, Nothing, String, String] {
    ZRef.makeManaged(0).map { ref =>
      {
        case None => UIO(Chunk.empty)
        case Some(c) =>
          ref.modify(s =>
            (c.zipWithIndexFrom(s).map((x, i) => s"$i $x"), s + c.length)
          )
      }
    }
  }

  def run(a: List[String]) =
    for
      // p1 <- Promise.make[Nothing, Unit]
      // p2 <- Promise.make[Nothing, Unit]
      f <- ZStream
        .fromSocketServer(1111, None)
        .mapMParUnordered(2) { c =>
          for
            hr <- ZHub.unbounded[String].map(_.map(_.hashCode))
            hw <- ZHub.unbounded[Int].map(_.map(_.toString))
            f1 <- c.read
              .tap(x => putStrLn(s"Processing: $x"))
              .map(_.toString)
              // .transduce(ZTransducer.utf8Decode >>> ZTransducer.splitLines)
              .foreach(s => hr.publish(s))
              .fork
            f3 <- ZStream
              .fromHub(hw)
              .foreach(x =>
                putStrLn(s"Sending: $x") *> Stream(x + '\n')
                  .mapConcat(_.getBytes)
                  .run(c.write)
              ).fork
            f2 <- ZStream
              .fromHub(hr)
              .foreach(i => putStrLn(s"Received: $i") *> hw.publish(i * 10))
              // .fork
            _ <- c.close()
          yield ()
        }
        .runDrain
        .orDie
        .fork
      _ <- f.join
    yield ExitCode.success
