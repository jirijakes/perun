package perun.db.tinkerpop

import org.apache.tinkerpop.gremlin.process.traversal.AnonymousTraversalSource.traversal
import org.apache.tinkerpop.gremlin.process.traversal.IO.graphml
import org.apache.tinkerpop.gremlin.process.traversal.Operator.*
import org.apache.tinkerpop.gremlin.process.traversal.Order.*
import org.apache.tinkerpop.gremlin.process.traversal.P.*
import org.apache.tinkerpop.gremlin.process.traversal.Pop.*
import org.apache.tinkerpop.gremlin.process.traversal.SackFunctions.*
import org.apache.tinkerpop.gremlin.process.traversal.Scope.*
import org.apache.tinkerpop.gremlin.process.traversal.TextP.*
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.*
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.*
import org.apache.tinkerpop.gremlin.structure.Column.*
import org.apache.tinkerpop.gremlin.structure.Direction.*
import org.apache.tinkerpop.gremlin.structure.T.*
import org.apache.tinkerpop.gremlin.structure.Vertex
import org.apache.tinkerpop.gremlin.tinkergraph.structure.*
import zio.*

import perun.db.p2p.P2P
import perun.proto.codecs.*
import perun.proto.gossip.{ChannelAnnouncement, NodeAnnouncement}

def gremlin: ZLayer[Has[GraphTraversalSource], Nothing, Has[P2P]] =
  ZLayer.fromService(g => new GremlinP2P(g))

/** Layer for creating an in-memory Tinkergraph database suitable for Gremlin.
  */
def tinkergraph: ULayer[Has[GraphTraversalSource]] =
  ZManaged
    .make(
      UIO(traversal().withEmbedded(TinkerGraph.open()))
    )(g =>
      (IO(g.io("graf.xml").write().iterate()) *> IO(println("KONEC")) *> IO(
        g.close()
      )).orDie
    )
    .toLayer

def inMemory = tinkergraph >>> gremlin

class GremlinP2P(g: GraphTraversalSource) extends P2P:
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
      .property("alias", n.alias.toString)
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

// import scala.compiletime.*

// trait Grem[A, Name <: String]:
//   def properties(a: A): GraphTraversal[Nothing, Nothing]
//   inline def x: String = scala.compiletime.constValue[Name]

// given Grem[NodeAnnouncement, "node"] =
//   new Grem:
//     def properties(n: NodeAnnouncement) =
//       __.property("name", "John")
