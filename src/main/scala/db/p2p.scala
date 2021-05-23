package perun.db

import zio.*

import perun.proto.gossip.{ChannelAnnouncement, NodeAnnouncement}

type P2P = Has[P2P.Service]

object P2P:
  trait Service:
    def offerNode(n: NodeAnnouncement): IO[Throwable, Unit]
    def offerChannel(c: ChannelAnnouncement): IO[Throwable, Unit]
// def offerChannelUpdate

def offerNode(n: NodeAnnouncement): ZIO[P2P, Throwable, Unit] =
  ZIO.accessM(_.get.offerNode(n))

def offerChannel(c: ChannelAnnouncement): ZIO[P2P, Throwable, Unit] =
  ZIO.accessM(_.get.offerChannel(c))
