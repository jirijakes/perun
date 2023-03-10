package perun.db.tinkerpop

import org.apache.tinkerpop.gremlin.process.traversal.AnonymousTraversalSource.traversal
import org.apache.tinkerpop.gremlin.process.traversal.IO.graphml
// import org.apache.tinkerpop.gremlin.process.traversal.Operator.*
// import org.apache.tinkerpop.gremlin.process.traversal.Order.*
import org.apache.tinkerpop.gremlin.process.traversal.P.*
// import org.apache.tinkerpop.gremlin.process.traversal.Pop.*
import org.apache.tinkerpop.gremlin.process.traversal.SackFunctions.*
// import org.apache.tinkerpop.gremlin.process.traversal.Scope.*
import org.apache.tinkerpop.gremlin.process.traversal.TextP.*
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.*
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.*
// import org.apache.tinkerpop.gremlin.structure.Column.*
// import org.apache.tinkerpop.gremlin.structure.Direction.*
// import org.apache.tinkerpop.gremlin.structure.T.*
import org.apache.tinkerpop.gremlin.structure.Vertex
import org.apache.tinkerpop.gremlin.tinkergraph.structure.*
import zio.*
import scala.jdk.CollectionConverters.*

import perun.db.p2p.P2P
import perun.p2p.*
import perun.proto.codecs.*
import perun.proto.gossip.{ChannelAnnouncement, ChannelUpdate, NodeAnnouncement}
import scala.reflect.ClassTag

def gremlin: ZLayer[GraphTraversalSource, Nothing, P2P] =
  ZLayer.fromFunction(Gremlin.apply)

def cast[T: ClassTag](v: Any): Option[T] =
  v match
    case t: T => Some(t)
    case _    => None

/** Layer for creating an in-memory Tinkergraph database suitable for Gremlin.
  */
def tinkergraph: ZLayer[Any, Throwable, GraphTraversalSource] =
  ZLayer.scoped {
    ZIO
      .acquireRelease(
        ZIO
          .succeed(traversal().withEmbedded(TinkerGraph.open()))
        // .tap(g => ZIO.attemptBlocking(g.io("init.xml").read().iterate()))
      )(g =>
        ZIO.attemptBlocking(g.io("graf.xml").write().iterate()).ignore *>
          ZIO.succeed(g.close())
      )
  }

final case class Gremlin(g: GraphTraversalSource) extends P2P:
  def findNode(nodeId: NodeId): Task[Option[Node]] =
    val res = g
      .V()
      .has("node", "id", nodeId.hex)
      .local(
        properties("id", "timestamp", "blacklisted")
          .group()
          .by(key())
          .by(value())
      )
      .toList()

    val node =
      res.asScala
        .map { p =>
          val m = p.asScala.toMap[AnyRef, Any]

          for
            id <- m.get("id").flatMap(cast[String])
            ts <- m.get("timestamp").flatMap(cast[Long]).orElse(Some(0L))
            bl <- m
              .get("blacklisted")
              .flatMap(cast[Boolean])
              .orElse(Some(false))
          yield Node(NodeId.fromHex(id), Timestamp.fromTimestamp(ts), bl)
        }
        .toList
        .headOption
        .flatten

    ZIO.succeed(node)

  def findChannels(nodeId: NodeId): Task[List[Channel]] =
    val res = g
      .E()
      .hasLabel("channel")
      .where(bothV().has("node", "id", nodeId.hex))
      .project("shortChannelId", "from", "to")
      .by(values("shortChannelId"))
      .by(outV().values("id"))
      .by(inV().values("id"))
      .toList()

    val channels =
      res.asScala
        .map { p =>
          val m = p.asScala.toMap[String, Any]
          for
            f <- m.get("from").flatMap(cast[String])
            t <- m.get("to").flatMap(cast[String])
            i <- m.get("shortChannelId").flatMap(cast[String])
            id <- ShortChannelId.fromString(i)
            ts = m.get("timestamp").flatMap(cast[Long])
          yield Channel(id, NodeId.fromHex(f), NodeId.fromHex(t), ts)
        }
        .toList
        .collect { case Some(c) => c }

    ZIO.succeed(channels)

  def findChannel(shortChannelId: ShortChannelId): Task[Option[Channel]] =
    def res = g
      .E()
      .has("channel", "shortChannelId", shortChannelId.toString)
      .project("shortChannelId", "timestamp", "from", "to")
      .by("shortChannelId")
      .by("timestamp")
      .by(outV().values("id"))
      .by(inV().values("id"))
      .toList()

    def channel =
      res.asScala
        .map { p =>
          val m = p.asScala.toMap[String, Any]

          for
            f <- m.get("from").flatMap(cast[String])
            t <- m.get("to").flatMap(cast[String])
            i <- m.get("shortChannelId").flatMap(cast[String])
            id <- ShortChannelId.fromString(i)
            ts = m.get("timestamp").flatMap(cast[Long])
          yield Channel(id, NodeId.fromHex(f), NodeId.fromHex(t), ts)
        }
        .toList
        .headOption
        .flatten

    import zio.Duration.*
    // TODO: Remove sleep when we understand why just inserted channel can't be retreived
    ZIO.sleep(50.millis) *> ZIO.succeed(channel)

  def updateChannel(c: ChannelUpdate): Task[Unit] =
    val ch = g
      .E()
      .has("channel", "shortChannelId", c.shortChannelId.toString)
      .property("cltvExpiryDelta", c.cltvExpiryDelta)
      .property("htlcMaximumMsat", c.htlcMaximumMsat)
      .property("htlcMinimumMsat", c.htlcMinimumMsat)
      .property("feeBaseMsat", c.feeBaseMsat)
      .property("feeProportionalMillionths", c.feeProportionalMillionths)
      .property("timestamp", c.timestamp)

    ZIO.attempt(ch.next()).unit

  // TODO: Perhaps we may store txout inside channel so we don't query it all the time
  def offerChannel(c: ChannelAnnouncement): Task[Unit] =
    val n1 = getOrCreateNode(c.nodeId1)
    val n2 = getOrCreateNode(c.nodeId2)
    val ch = g
      .E()
      .has("channel", "shortChannelId", c.shortChannelId.toString)
      .fold()
      .coalesce(
        unfold(),
        addE("channel")
          .from(n1)
          .to(n2)
          .property("shortChannelId", c.shortChannelId.toString)
      )

    ZIO.attempt(ch.next()).unit

  // TODO: Per specification, we should ignore node announcements for unknown channels
  // but we store everything now. In the future, it should be configurable.
  def offerNode(n: NodeAnnouncement): Task[Unit] =
    val n1 = g
      .V()
      .has("node", "id", n.nodeId.hex)
      .fold()
      .coalesce(
        unfold(),
        addV("node").property("id", n.nodeId.hex)
      )
      .property("color", n.color.hex)
      .property("alias", n.alias.asText.getOrElse(""))
      .property("timestamp", n.timestamp)

    ZIO.attempt(n1.next()).unit

def getOrCreateNode(id: NodeId): GraphTraversal[Nothing, Vertex] =
  V()
    .has("node", "id", id.hex)
    .fold()
    .coalesce(
      unfold(),
      addV("node").property("id", id.hex)
    )
