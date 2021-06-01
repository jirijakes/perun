package perun.db.store

import zio.*
import zio.stream.*
import zio.test.Assertion.*
import zio.test.*

object StoreTest extends DefaultRunnableSpec:

  import perun.db.store.*

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
        .provideCustomLayer(live("jdbc:hsqldb:mem:inmem"))
        .orDie

      assertM(y)(equalTo(Chunk(1, 2, 3)))
    }
  )
