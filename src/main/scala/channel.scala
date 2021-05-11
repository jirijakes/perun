import org.bitcoins.crypto._

opaque type Point = ECPublicKey

opaque type NodeId = Point

final case class Channel(
  remoteNode: NodeId
)
