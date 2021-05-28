import zio.*
import zio.console.*
import zio.stream.*

import org.bitcoins.core.protocol.script.*
import org.bitcoins.core.script.*

import scodec.bits.*
import org.bitcoins.core.script.constant.*
import org.bitcoins.core.script.crypto.*
import org.bitcoins.core.psbt.InputPSBTRecord.WitnessScript

object pokus: // extends App:

  def main(args: Array[String]): Unit =
    // val x = ScriptPubKey.fromAsm(List(constant.OP_TRUE))
    val x = List(OP_2, ScriptConstant("036497fee1a0b963a63f0b4374819d9016fcca9c09dbef4cb427a2d0146649b025"), ScriptConstant("03f917c3cb41b5fe349f806f973f4ef63c99d28f74a92a6365bd640cbc16644f88"), OP_2, OP_CHECKMULTISIG)
// val s = ScriptWitness.from
    // val z= 
    val s = P2WSHWitnessV0(RawScriptPubKey(x)) //.fromAsmHex("0020b90a5d705a77ef6799e0e6c9e1098167ddd534cfcf7d8e65c93c5cf1fb8bcb1b").asm
    println(">>> " + s)

  // def run(args: List[String]) =
  //   ZStream(1, 2, 3, 4)
  //     .mapM(x =>
  //       ZIO.fromEither(
  //         if x % 2 == 1 then Right(x)
  //         else Left(s"No $x")
  //       )
  //     )
  //     // .tap(x => putStrLn("A> " + x))
  //     .either
  //     .tap(x => putStrLn("B> " + x))
  //     .foreach(x => putStrLn("C> " + x))
  //     .exitCode


// ChannelAnnouncement(ECDigitalSignature(cd03ce51bf626a38eccfc165b0ae17f01d2d9be2e49bf8daaa9542fc16b3284e30952f376de24d743e671621ac31486e036cbea94cef36c341e02475cfccd982),ECDigitalSignature(709159beafc1cfa73cd7e69c5abdaacde394b726313cda664849927576bffe3a1559d757af154a10b14ea90fe300e5a5143ddfb721e2810659d0dc07d7fd736f),ECDigitalSignature(a7ca271c727aa88db5f98b686ffdac294ef973a5a6b68e069719c73d8af318e6028dd9bd481187aeb0e3102ad17ed003273ad8bb34b536e5b378cd7e05ed6832),ECDigitalSignature(3509f50ca038dca3854787d48fafd6bc9c95c9804a733fa478abe3f86b7f75c1333dbb24da0c47d003500e1d64fe48accd79d9426aa182e8f729a6290481e681),ByteVector(empty),Testnet,1976790x8x0,ECPublicKey(036497fee1a0b963a63f0b4374819d9016fcca9c09dbef4cb427a2d0146649b025),ECPublicKey(03f917c3cb41b5fe349f806f973f4ef63c99d28f74a92a6365bd640cbc16644f88),ECPublicKey(032ddc3d892921ffc611a8e0e1aaa31862186f1888b7d62804b88e35af60e57dba),ECPublicKey(03a3b22e0c0616fd16df98e5b40d7eb614137cb494241690149be225f0cefb80bc),ByteVector(empty))
