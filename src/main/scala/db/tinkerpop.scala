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
import perun.proto.gossip.{ChannelAnnouncement, NodeAnnouncement}

def gremlin: ZLayer[Has[GraphTraversalSource], Nothing, Has[P2P]] =
  Gremlin.apply.toLayer

/** Layer for creating an in-memory Tinkergraph database suitable for Gremlin.
  */
def tinkergraph: ULayer[Has[GraphTraversalSource]] =
  ZManaged
    .make(
      UIO(traversal().withEmbedded(TinkerGraph.open())).tap(g =>
        ZIO.effectTotal(g.io("init.xml").read().iterate())
      )
    )(g =>
      (IO(g.io("graf.xml").write().iterate()) *> IO(println("KONEC")) *> IO(
        g.close()
      )).orDie
    )
    .toLayer

final case class Gremlin(g: GraphTraversalSource) extends P2P:
  def findNode(nodeId: NodeId): Task[Option[Node]] =
    val res = g
      .V()
      .has("node", "id", nodeId.hex)
      .valueMap("id", "timestamp")
      .toList()

    val node =
      res.asScala
        .map { p =>
          val m = p.asScala.toMap[AnyRef, Any]

          for
            (id: java.util.ArrayList[String] @unchecked) <- m.get("id")
            (ts: java.util.ArrayList[Long] @unchecked) <- m.get("timestamp")
          yield Node(
            NodeId.fromHex(id.get(0)),
            Timestamp.fromTimestamp(ts.get(0))
          )
        }
        .toList
        .headOption
        .flatten

    UIO.succeed(node)

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
            (f: String) <- m.get("from")
            (t: String) <- m.get("to")
            (i: String) <- m.get("shortChannelId")
            id <- ShortChannelId.fromString(i)
          yield Channel(id, NodeId.fromHex(f), NodeId.fromHex(t))
        }
        .toList
        .collect { case Some(c) => c }

    UIO.succeed(channels)

  def findChannel(shortChannelId: ShortChannelId): Task[Option[Channel]] =
    val res = g
      .E()
      .has("channel", "shortChannelId", shortChannelId.toString)
      .project("from", "to")
      .by(outV().values("id"))
      .by(inV().values("id"))
      .toList()

    val channel =
      res.asScala
        .map { p =>
          val m = p.asScala
          m.get("from")
            .flatMap((f: String) =>
              m.get("to")
                .map((t: String) =>
                  Channel(shortChannelId, NodeId.fromHex(f), NodeId.fromHex(t))
                )
            )
        }
        .toList
        .headOption
        .flatten

    UIO(channel)

  def offerChannel(c: ChannelAnnouncement): Task[Unit] =
    val n1 = getOrCreateNode(c.nodeId1)
    val n2 = getOrCreateNode(c.nodeId2)
    val ch = g
      .addE("channel")
      .from(n1)
      .to(n2)
      .property("shortChannelId", c.shortChannelId.toString)
    Task(ch.next()).unit

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
    // println(">>> " + n1)
    Task(n1.next())
      // .tapCause(e => Task(println("######> " + e.prettyPrint)))
      .unit

def getOrCreateNode(id: NodeId): GraphTraversal[Nothing, Vertex] =
  V()
    .has("node", "id", id.hex)
    .fold()
    .coalesce(
      unfold(),
      addV("node").property("id", id.hex)
    )
