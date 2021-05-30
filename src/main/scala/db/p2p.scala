package perun.db.p2p

import zio.*

import perun.proto.gossip.{ChannelAnnouncement, NodeAnnouncement}

trait P2P:
  def offerNode(n: NodeAnnouncement): IO[Throwable, Unit]
  def offerChannel(c: ChannelAnnouncement): IO[Throwable, Unit]
// def offerChannelUpdate

def offerNode(n: NodeAnnouncement): ZIO[Has[P2P], Throwable, Unit] =
  ZIO.accessM(_.get.offerNode(n))

def offerChannel(c: ChannelAnnouncement): ZIO[Has[P2P], Throwable, Unit] =
  ZIO.accessM(_.get.offerChannel(c))
