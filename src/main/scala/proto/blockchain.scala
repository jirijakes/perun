package perun.proto.blockchain

import scodec.*
import scodec.bits.*
import scodec.codecs.*

enum Chain:
  case Signet
  case Testnet

val signetHash =
  hex"f61eee3b63a380a477a063af32b2bbc97c9ff9f01f2c4225e973988108000000"

val testnetHash =
  hex"43497fd7f826957108f4a30fd9cec3aeba79972084e90ead01ea330900000000"

val chain: Codec[Chain] = bytes(32).exmap(
  {
    case b if b == signetHash  => Attempt.Successful(Chain.Signet)
    case b if b == testnetHash => Attempt.Successful(Chain.Testnet)
    case _                     => Attempt.Failure(Err("Unknown chain hash"))
  },
  {
    case Chain.Signet  => Attempt.Successful(signetHash)
    case Chain.Testnet => Attempt.Successful(testnetHash)
  }
)
