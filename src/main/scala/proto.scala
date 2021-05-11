import scodec._
import scodec.bits._
import scodec.codecs._

opaque type ChannelId = ByteVector

case class AllChannels()

object ChannelId {
  def make(bytes: ByteVector): Either[String, ChannelId] =
    if bytes.length == 32 then Right(bytes) else Left("must be 32")
}

extension (c: ChannelId) def toBytes: ByteVector = c

val channelId: Codec[ChannelId] = bytes(32)

case class Error(channel: ChannelId | AllChannels, data: String)

val allChannels: ByteVector = ByteVector.fill(32)(0)

val error: Codec[Error] =
  (channelId :: variableSizeBytes(uint16, ascii))
    .xmap(
      {
        case (c, s) if c == allChannels =>
          Error(AllChannels(), s)
        case (c, s) => Error(c, s)
      },
      {
        case Error(c: ChannelId, d)  => (c, d)
        case Error(AllChannels(), d) => (allChannels, d)
      }
    )
