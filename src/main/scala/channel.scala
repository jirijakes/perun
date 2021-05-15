import org.bitcoins.crypto.*

opaque type Point = ECPublicKey

opaque type NodeId = Point

final case class Channel(
    remoteNode: NodeId
)
