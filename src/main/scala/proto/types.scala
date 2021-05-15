package perun.proto

import scodec.Codec
import scodec.codecs.*

import perun.proto.codecs.*

object tlv:

  final case class Tlv()

  val codec = (bigsize :: variableSizeBytesLong(bigsize, bytes))
