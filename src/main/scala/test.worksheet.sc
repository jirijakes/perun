import zio.prelude.*


val x: State[String, Int] = State.succeed(1)

val z =
  for
    // a <- x
    b <- State.modify(_ + 1)  
  yield b
