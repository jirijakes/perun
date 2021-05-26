import zio.*
import zio.console.*

import perun.crypto.secp256k1.*

import scodec.bits.*
import com.sun.jna.*
import jnr.ffi.byref.PointerByReference

object pokus:

  val lib = Native.load("secp256k1", classOf[unsafe.Secp256k1])

  def main(args: Array[String]): Unit =
    val c = lib.secp256k1_context_create(unsafe.flagSign | unsafe.flagVerify)

    val valid = ByteVector.fromValidHex("025884b3a24b9737889238a62662523511d09aa11b800b5e93802611ef674bd923")

    // val valid = ByteVector.fromValidHex("0000000000000000000000000000425200000000000000000000000000000000000064efa17b7761e1e42706989fb483b8d2d49bf78fae9803f099b834edeb00")



    val in = valid.toArray

    val p = unsafe.Pubkey.empty
    val out = Array.ofDim[Byte](128)
    val pk = lib.secp256k1_ec_pubkey_parse(c, p, in, in.length)
    val ec = lib.secp256k1_ecdh(c, out, p, Array.fill[Byte](64)(0), Pointer.NULL, Pointer.NULL)
    println(">>> " + ec)
    println(">>> " + out)

    lib.secp256k1_context_destroy(c)

// def run(args: List[String]) =
//   verifySecureKey(Array.fill[Byte](1)(0) :+ 1)
//     .flatMap(r =>
//       putStrLn(
//         ">>> " + r
//       )
//     )
//     .provideCustomLayer(native)
//     .exitCode
