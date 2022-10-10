package perun.crypto

import zio.test.*

object testSuite extends ZIOSpecDefault:
  val spec =
    suite("crypto")(
      chacha.testSuite.spec,
      secp256k1.testSuite.spec
    )
