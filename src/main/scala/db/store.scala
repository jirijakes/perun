package perun.db.store

import java.sql.{DriverManager, ResultSet}

import org.hsqldb.*
import zio.blocking.*
import zio.stream.*
import zio.{blocking as _, *}

trait Store:
  def execute(stmt: String): Task[Unit]
  def query[A](q: String, f: ResultSet => A): Stream[Throwable, A]

def live(connection: String): ZLayer[Blocking, Throwable, Has[Store]] =
  ZManaged
    .make {
      blocking {
        Task(
          DriverManager.getConnection(
            connection /* "jdbc:hsqldb:file:testdb" */,
            "SA",
            ""
          )
        )
      }
    }(c => Task(c.close()).orDie)
    .mapM(x => ZIO.environment[Blocking].map(b => (b.get, x)))
    .map((b, c) =>
      new Store:
        def execute(stmt: String): Task[Unit] =
          Task(c.createStatement.execute(stmt)).unit

        def query[A](
            q: String,
            f: ResultSet => A
        ): Stream[Throwable, A] =
          val open = b.blocking {
            for
              s <- Task(c.prepareStatement(q))
              rs <- Task(s.executeQuery)
            yield rs

          }
          ZStream
            .managed(ZManaged.make(open)(rs => Task(rs.close).orDie))
            .flatMap { rs =>
              ZStream.unfoldM(rs)(rs =>
                Task(rs.next).flatMap { r =>
                  if r then b.blocking(Task(Some(f(rs), rs)))
                  else UIO(None)
                }
              )
            }
    )
    .toLayer

def execute(stmt: String): ZIO[Has[Store], Throwable, Unit] =
  ZIO.accessM(_.get.execute(stmt))

def query[A](q: String, f: ResultSet => A): ZStream[Has[Store], Throwable, A] =
  ZStream.accessStream(_.get.query(q, f))
