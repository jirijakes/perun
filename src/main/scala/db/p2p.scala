package perun.db.p2p

import zio.*

import perun.p2p.*
import perun.proto.gossip.{ChannelAnnouncement, NodeAnnouncement}

trait P2P:
  def offerNode(n: NodeAnnouncement): IO[Throwable, Unit]
  def offerChannel(c: ChannelAnnouncement): IO[Throwable, Unit]
  def findChannel(shortId: ShortChannelId): IO[Throwable, Option[Channel]]
  def findChannels(nodeId: NodeId): IO[Throwable, List[Channel]]
  def findNode(nodeId: NodeId): IO[Throwable, Option[Node]]

object P2P:
  import perun.db.tinkerpop.*
  def inMemory: ZLayer[Any, Throwable, P2P] = tinkergraph >>> gremlin

def offerNode(n: NodeAnnouncement): ZIO[P2P, Throwable, Unit] =
  ZIO.serviceWithZIO(_.offerNode(n))

def offerChannel(c: ChannelAnnouncement): ZIO[P2P, Throwable, Unit] =
  ZIO.serviceWithZIO(_.offerChannel(c))

def findChannel(
    shortChannelId: ShortChannelId
): ZIO[P2P, Throwable, Option[Channel]] =
  ZIO.serviceWithZIO(_.findChannel(shortChannelId))

def findChannels(nodeId: NodeId): ZIO[P2P, Throwable, List[Channel]] =
  ZIO.serviceWithZIO(_.findChannels(nodeId))

def findNode(nodeId: NodeId): ZIO[P2P, Throwable, Option[Node]] =
  ZIO.serviceWithZIO(_.findNode(nodeId))
