import scala.jdk.CollectionConverters.*

import org.apache.tinkerpop.gremlin.process.traversal.AnonymousTraversalSource.traversal
import org.apache.tinkerpop.gremlin.process.traversal.Operator.*
import org.apache.tinkerpop.gremlin.process.traversal.Order.*
import org.apache.tinkerpop.gremlin.process.traversal.P.*
import org.apache.tinkerpop.gremlin.process.traversal.Pop.*
import org.apache.tinkerpop.gremlin.process.traversal.SackFunctions.*
import org.apache.tinkerpop.gremlin.process.traversal.Scope.*
import org.apache.tinkerpop.gremlin.process.traversal.TextP.*
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.*
import org.apache.tinkerpop.gremlin.structure.Column.*
import org.apache.tinkerpop.gremlin.structure.Direction.*
import org.apache.tinkerpop.gremlin.structure.T.*
import org.apache.tinkerpop.gremlin.structure.Vertex
import org.apache.tinkerpop.gremlin.tinkergraph.structure.*
import scodec.bits.*
import zio.*
import zio.console.*

import perun.db.*
import perun.db2.orient.*

object pokus:

  def main(args: Array[String]) =
    val g = traversal().withEmbedded(TinkerGraph.open())
    // val p1 = g.addV("person").property("name", "John")

    val _ = g.addV("node").property("id", "node 2").next()

    val n1 = V()
      .has("node", "id", "node 1")
      .fold()
      .coalesce(
        unfold(),
        addV("node").property("id", "node 1").property("new", true)
      )

    val n2 = V()
      .has("node", "id", "node 2")
      .fold()
      .coalesce(
        unfold(),
        addV("node").property("id", "node 2").property("new", true)
      )

    val c = g.addE("channel").from(n1).to(n2).next()

    println("#> " + g.V().properties().toList().asScala.toList)
    println("#> " + g.E().toList().asScala.toList)

    g.close()
