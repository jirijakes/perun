package perun.db.store

import java.sql.{DriverManager, ResultSet}

import org.hsqldb.*
import zio.stream.*
import zio.*

trait Store:
  def execute(stmt: String): Task[Unit]
  def query[A](q: String, f: ResultSet => A): Stream[Throwable, A]

def live(connection: String): ZLayer[Any, Throwable, Store] =
  ZLayer.scoped {
    ZIO
      .acquireRelease {
        ZIO.attemptBlocking(
          DriverManager.getConnection(
            connection /* "jdbc:hsqldb:file:testdb" */,
            "SA",
            ""
          )
        )

      }(c => ZIO.attempt(c.close()).orDie)
      // .flatMap(x => ZIO.environment[Blocking].map(b => (b.get, x)))
      .map(c =>
        new Store:
          def execute(stmt: String): Task[Unit] =
            ZIO.attemptBlocking(c.createStatement.execute(stmt)).unit

          def query[A](
              q: String,
              f: ResultSet => A
          ): Stream[Throwable, A] =
            val open = ZIO.blocking {
              for
                s <- ZIO.attempt(c.prepareStatement(q))
                rs <- ZIO.attempt(s.executeQuery)
              yield rs

            }
            ZStream
              .scoped(
                ZIO.acquireRelease(open)(rs => ZIO.attempt(rs.close).orDie)
              )
              .flatMap { rs =>
                ZStream.unfoldZIO(rs)(rs =>
                  ZIO.attempt(rs.next).flatMap { r =>
                    if r then ZIO.attemptBlocking(Some(f(rs), rs))
                    else ZIO.none
                  }
                )
              }
      )
  }

def execute(stmt: String): ZIO[Store, Throwable, Unit] =
  ZIO.serviceWithZIO(_.execute(stmt))

def query[A](q: String, f: ResultSet => A): ZStream[Store, Throwable, A] =
  ZStream.serviceWithStream(_.query(q, f))
