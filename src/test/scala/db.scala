package perun.db

import zio.*
import zio.stream.*
import zio.test.*

import Assertion.*

object store extends DefaultRunnableSpec:

  import perun.db.*

  val spec = suite("db")(
    testM("primitive") {
      val x =
        ZStream
          .fromEffect(
            execute("CREATE TABLE IF NOT EXISTS tbl (id INT)") *>
              execute("INSERT INTO tbl (id) VALUES ((1), (3))") *>
              execute("INSERT INTO tbl (id) VALUES (2)")
          ) *>
        query("SELECT * FROM tbl ORDER BY id", _.getInt(1))

      val y = x.runCollect
        .provideCustomLayer(Store.live("jdbc:hsqldb:mem:inmem"))
        .orDie

      assertM(y)(equalTo(Chunk(1, 2, 3)))
    }
  )
