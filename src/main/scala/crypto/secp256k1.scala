package perun.crypto.secp256k1

import scala.annotation.targetName

import com.sun.jna.*
import org.bitcoins.crypto.HashDigest
import scodec.bits.ByteVector
import zio.*

import perun.crypto.*
import perun.p2p.*
import perun.proto.codecs.*

enum Error:
  case LibraryNotFound
  case CouldNotInitialize

trait Secp256k1:
  def ecdh(sec: PrivateKey, pub: PublicKey): ByteVector
  def sign(sec: PrivateKey, h: HashDigest): Signature
  def verify(
      s: Signature,
      h: HashDigest,
      k: NodeId | PublicKey
  ): Boolean

def verifySignature(
    s: Signature,
    h: HashDigest,
    k: NodeId | PublicKey
): URIO[Secp256k1, Boolean] = ZIO.serviceWith[Secp256k1](_.verify(s, h, k))

def signMessage(
    sec: PrivateKey,
    h: HashDigest
): URIO[Secp256k1, Signature] = ZIO.serviceWith[Secp256k1](_.sign(sec, h))

case class NativeSecp256k1(lib: unsafe.Secp256k1, ctx: unsafe.Context)
    extends Secp256k1:
  def ecdh(sec: PrivateKey, pub: PublicKey): ByteVector =
    val key = unsafe.Pubkey.allocate
    val keyr = lib.secp256k1_ec_pubkey_parse(
      ctx,
      key,
      pub.publicKeyToBytes.toArray,
      pub.byteSize.toInt
    )
    val out = Array.ofDim[Byte](32)
    val res = lib.secp256k1_ecdh(
      ctx,
      out,
      key,
      sec.secretKeyToBytes.toArray,
      Pointer.NULL,
      Pointer.NULL
    )
    ByteVector.view(out)
  def sign(sec: PrivateKey, h: HashDigest): Signature =
    val sig = unsafe.Signature.allocate
    val resr = lib.secp256k1_ecdsa_sign(
      ctx,
      sig,
      h.bytes.toArray,
      sec.secretKeyToBytes.toArray,
      Pointer.NULL,
      Pointer.NULL
    )
    val out = Array.ofDim[Byte](64)
    val com =
      lib.secp256k1_ecdsa_signature_serialize_compact(ctx, out, sig)
    Signature.fromBytes(ByteVector.view(out))
  def verify(
      s: Signature,
      h: HashDigest,
      k: NodeId | PublicKey
  ): Boolean =
    val sig = unsafe.Signature.allocate
    val sigr = lib.secp256k1_ecdsa_signature_parse_compact(
      ctx,
      sig,
      s.signatureToBytes.toArray
    )
    val key = unsafe.Pubkey.allocate
    val keyr = lib.secp256k1_ec_pubkey_parse(
      ctx,
      key,
      k match
        // case n: NodeId    => n.nodeIdAsPublicKey.bytes.toArray
        case p: PublicKey => p.publicKeyToBytes.toArray
      ,
      k match
        // case n: NodeId    => n.nodeIdAsPublicKey.byteSize.toInt
        case p: PublicKey => p.publicKeyToBytes.size.toInt
    )
    val verr = lib.secp256k1_ecdsa_verify(ctx, sig, h.bytes.toArray, key)
    verr == 1

def native: ZLayer[Any, Error, Secp256k1] =
  ZLayer.scoped {
    ZIO
      .acquireRelease(
        for
          lib <- ZIO
            .attempt(Native.load("secp256k1", classOf[unsafe.Secp256k1]))
            .refineOrDie { case _: UnsatisfiedLinkError =>
              Error.LibraryNotFound
            }
          ctx <- ZIO
            .attempt(
              lib.secp256k1_context_create(
                unsafe.flagSign | unsafe.flagVerify
              )
            )
            .refineOrDie { case x => Error.CouldNotInitialize }
        // cb = new lib.OnError:
        // def invoke(message: String, data: Pointer) =
        // println(">>>>>>>> " + message)
        // _ <- Task(
        // lib.secp256k1_context_set_illegal_callback(ctx, cb, Pointer.NULL)
        // ).orDie
        yield (ctx, lib)
      )((ctx, lib) => ZIO.attempt(lib.secp256k1_context_destroy(ctx)).ignore)
      .map { (ctx, lib) => NativeSecp256k1(lib, ctx) }
  }

object unsafe:

  opaque type Context = Pointer
  opaque type Signature = Array[Byte]
  opaque type Pubkey = Array[Byte]

  object Pubkey:
    def allocate: Pubkey = Array.ofDim[Byte](64)

  object Signature:
    def allocate: Signature = Array.ofDim[Byte](64)

  val flagVerify = (1 << 0) | (1 << 8)
  val flagSign = (1 << 0) | (1 << 9)

  // format: off
  trait Secp256k1 extends Library:
    def secp256k1_context_create(i: Int): Context
    def secp256k1_context_destroy(c: Context): Unit
    def secp256k1_context_set_illegal_callback(c: Context, f: OnError, p: Pointer): Unit
    def secp256k1_ec_pubkey_parse(c: Context, k: Pubkey, in: Array[Byte], size: Int): Int
    def secp256k1_ecdh(c: Context, out: Array[Byte], pub: Pubkey, sec: Array[Byte], hash: Pointer, data: Pointer): Int
    def secp256k1_ecdsa_sign(c: Context, s: Signature, h: Array[Byte], k: Array[Byte], n: Pointer, data: Pointer): Int
    def secp256k1_ecdsa_signature_parse_compact(c: Context, s: Signature, in: Array[Byte]): Int
    def secp256k1_ecdsa_signature_serialize_compact(c: Context, s: Array[Byte], in: Signature): Int
    def secp256k1_ecdsa_verify(c: Context, s: Signature, h: Array[Byte], k: Pubkey): Int

    trait OnError extends Callback:
      def invoke(message: String, data: Pointer): Unit
