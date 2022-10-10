package perun.proto.blockchain

import scodec.*
import scodec.bits.*
import scodec.codecs.*

enum Chain:
  case Mainnet
  case Signet
  case Testnet
  case Regtest

val mainnetHash =
  hex"6fe28c0ab6f1b372c1a6a246ae63f74f931e8365e15a089c68d6190000000000"

val signetHash =
  hex"f61eee3b63a380a477a063af32b2bbc97c9ff9f01f2c4225e973988108000000"

val testnetHash =
  hex"43497fd7f826957108f4a30fd9cec3aeba79972084e90ead01ea330900000000"

val regtestHash =
  hex"06226e46111a0b59caaf126043eb5bbf28c34f3a5e332a1fc7b2b73cf188910f"

val chain: Codec[Chain] = bytes(32).exmap(
  {
    case b if b == signetHash  => Attempt.Successful(Chain.Signet)
    case b if b == testnetHash => Attempt.Successful(Chain.Testnet)
    case b if b == mainnetHash => Attempt.Successful(Chain.Mainnet)
    case b if b == regtestHash => Attempt.Successful(Chain.Regtest)
    case _                     => Attempt.Failure(Err("Unknown chain hash"))
  },
  {
    case Chain.Mainnet => Attempt.Successful(mainnetHash)
    case Chain.Signet  => Attempt.Successful(signetHash)
    case Chain.Testnet => Attempt.Successful(testnetHash)
    case Chain.Regtest => Attempt.Successful(regtestHash)
  }
)
