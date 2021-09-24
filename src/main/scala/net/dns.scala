package perun.net.dns

import org.xbill.DNS.lookup.LookupSession

import zio.*
import org.xbill.DNS.Name
import org.xbill.DNS.Type

import scala.jdk.CollectionConverters.*
import org.xbill.DNS.MXRecord
import org.xbill.DNS.ARecord
import org.xbill.DNS.SRVRecord

trait Dns:
  def lookup(s: String): ZIO[Any, Throwable, String]

final case class DnsJava(session: LookupSession) extends Dns:
  def lookup(s: String) =
    ZIO
      .fromCompletionStage(session.lookupAsync(Name.fromString(s), Type.SRV))
      .map(a =>
        a.getRecords.asScala
          .collect {
            case r: SRVRecord => r.toString
          }
          .mkString("\n")
      )

val live: ZLayer[Any, Nothing, Has[Dns]] =
  ZLayer.succeed(DnsJava(LookupSession.defaultBuilder.build))

def lookup(s: String) = ZIO.serviceWith[Dns](_.lookup(s))

object DDD extends zio.App:
  def run(r: List[String]) = lookup("nodes.lightning.directory")
    .flatMap(r => zio.console.putStrLn(r))
    .provideCustomLayer(live)
    .exitCode
