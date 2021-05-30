package perun.p2p

import perun.proto.codecs.*

final case class Channel(
    shortChannelId: ShortChannelId,
    node1: NodeId,
    node2: NodeId
)
