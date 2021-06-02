package perun.crypto

import zio.test.*

object test extends DefaultRunnableSpec:
  val spec =
    suite("crypto")(
      chacha.test.spec,
      secp256k1.test.spec
    )
