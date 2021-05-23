package perun.db2.orient

import com.orientechnologies.orient.core.metadata.schema.OType
import com.tinkerpop.blueprints.impls.orient.*
import zio.*

import perun.db.*
import perun.proto.gossip.NodeAnnouncement
import com.orientechnologies.orient.core.sql.OCommandSQL
import perun.proto.gossip.ChannelAnnouncement

def embedded: ZLayer[Any, Throwable, P2P] =
  Managed
    .make(Task(new OrientGraph("plocal:orientacni")))(f =>
      Task(f.shutdown()).orDie
    )
    .tapM(initTypes)
    .map(g => newDB(g))
    .toLayer

def newDB(db: OrientGraph): P2P.Service = new P2P.Service:
  def offerNode(n: NodeAnnouncement): Task[Unit] =
    Task {
      val _ = db.addVertex("class:Node", "publicKey", "Ha")
    }
  def offerChannel(c: ChannelAnnouncement): Task[Unit] = Task.unit

def initTypes(db: OrientGraph): Task[Unit] =
  // val x = db.command(new OCommandSQL("")).execute()
  if db.getVertexType("Node") == null then
    Task {
      val n = db.createVertexType("Node")
      val _ = n.createProperty("publicKey", OType.BINARY)
    }
  else Task.unit
