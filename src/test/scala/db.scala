package perun.db.store

import zio.*
import zio.stream.*
import zio.test.Assertion.*
import zio.test.*

object StoreTest extends ZIOSpecDefault:

  import perun.db.store.*

  val spec = suite("db")(
    test("primitive") {
      val x =
        ZStream
          .fromZIO(
            execute("CREATE TABLE IF NOT EXISTS tbl (id INT)") *>
              execute("INSERT INTO tbl (id) VALUES ((1), (3))") *>
              execute("INSERT INTO tbl (id) VALUES (2)")
          ) *>
          query("SELECT * FROM tbl ORDER BY id", _.getInt(1))

      val y = x.runCollect
        .provideLayer(live("jdbc:hsqldb:mem:inmem"))
        .orDie

      assertZIO(y)(equalTo(Chunk(1, 2, 3)))
    }
  )
