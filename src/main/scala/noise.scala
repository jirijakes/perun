package noise

import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

import fr.acinq.secp256k1.Secp256k1
import org.bitcoins.crypto.CryptoUtil.sha256
import org.bitcoins.crypto.*
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.macs.HMac
import org.bouncycastle.crypto.params.KeyParameter
import scodec.bits.ByteVector
import zio.*

import crypto.keygen.*

/*
XK:
  <- s
  ...
  -> e, es
  <- e, ee
  -> s, se
 */

@FunctionalInterface
trait Binary[T]:
  def toBytes(t: T): ByteVector
  extension [T](t: T)(using binary: Binary[T]) def bytes = binary.toBytes(t)

object Binary:
  given Binary[ByteVector] = identity
  given Binary[String] = s => ByteVector.view(s.getBytes)
  given Binary[Array[Byte]] = ByteVector.view
  given Binary[ECPublicKey] = _.compressed.bytes

enum HandshakeError:
  case InvalidCiphertext

enum Token:
  case E, EE, SE, S, ES

import Token.*

val handshakePattern = List(List(E, ES), List(E, EE), List(S, SE))

enum HandshakeRole:
  case Initiator, Responder

enum CipherState:
  case Empty
  case Running(k: ByteVector, n: BigInt)

  def encryptWithAd(
      ad: ByteVector,
      plaintext: ByteVector
  ): (ByteVector, CipherState) = this match
    case Empty         => (plaintext, Empty)
    case Running(k, n) => (encrypt(k, n, ad, plaintext), Running(k, n + 1))

  def decryptWithAd(
      ad: ByteVector,
      ciphertext: ByteVector
  ): (ByteVector, CipherState) = this match
    case Empty         => (ciphertext, Empty)
    case Running(k, n) => (decrypt(k, n, ad, ciphertext), Running(k, n + 1))

final class SymmetricState(
    ck: ByteVector,
    h: ByteVector,
    cipher: CipherState
):
  def mixHash[B: Binary](data: B): SymmetricState =
    new SymmetricState(ck, sha256(h ++ data.bytes).bytes, cipher)

  def mixKey[B: Binary](data: B): SymmetricState =
    val (ck1, temp) = hkdf(ck, data.bytes)
    new SymmetricState(ck1, h, CipherState.Running(temp, 0))

  def cip(newCipherState: CipherState): SymmetricState =
    new SymmetricState(ck, h, newCipherState)

  def encryptAndHash(plaintext: ByteVector): (ByteVector, SymmetricState) =
    val (ciphertext, nextCipher) = cipher.encryptWithAd(h, plaintext)
    (ciphertext, mixHash(ciphertext).cip(nextCipher))

  def decryptAndHash(ciphertext: ByteVector): (ByteVector, SymmetricState) =
    val (plaintext, nextCipher) = cipher.decryptWithAd(h, ciphertext)
    (plaintext, mixHash(ciphertext).cip(nextCipher))

  def split: (CipherState, CipherState) =
    val (tempk1, tempk2) = hkdf(ck, ByteVector.empty)
    (CipherState.Running(tempk1, 0), CipherState.Running(tempk2, 0))

object SymmetricState:
  def apply(protocolName: String, prologue: String): SymmetricState =
    val ck = sha256(protocolName).bytes
    new SymmetricState(ck, ck, CipherState.Empty).mixHash(prologue)
  end apply

def encrypt(
    key: ByteVector,
    nonce: BigInt,
    ad: ByteVector,
    plaintext: ByteVector
) =
  val ch = new chacha.ChaCha20Poly1305()
  val n = Array.fill[Byte](4)(0) ++ nonce.toByteArray.reverse.padTo[Byte](8, 0)
  ByteVector.view(
    ch.encrypt(k2k(key), n, ad.toArray, plaintext.toArray)
  )

def decrypt(
    key: ByteVector,
    nonce: BigInt,
    ad: ByteVector,
    ciphertext: ByteVector
) =
  val ch = new chacha.ChaCha20Poly1305()
  val n = Array.fill[Byte](4)(0) ++ nonce.toByteArray.reverse.padTo[Byte](8, 0)
  ByteVector.view(
    ch.decrypt(k2k(key), n, ad.toArray, ciphertext.toArray)
  )

def dh(v1: ECPrivateKey, v2: ECPublicKey) =
  ByteVector.view(Secp256k1.get().ecdh(v1.bytes.toArray, v2.bytes.toArray))

enum HandshakeResult:
  case Continue(m: ByteVector, st: HandshakeState)
  case Done(c1: CipherState, c2: CipherState)

final class HandshakeState(
    val s: ECPrivateKey,
    val e: Option[ECPrivateKey],
    val rs: Option[ECPublicKey],
    val re: Option[ECPublicKey],
    val role: HandshakeRole,
    patterns: List[List[Token]],
    symmetric: SymmetricState,
    val expected: List[Int]
):

  def mixHash[B: Binary](data: B): HandshakeState =
    sym(_.mixHash(data))

  def mixKey[B: Binary](data: B): HandshakeState =
    sym(_.mixKey(data))

  def encryptAndHash(plaintext: ByteVector): (ByteVector, HandshakeState) =
    val (ciphertext, ss) = this.symmetric.encryptAndHash(plaintext)
    (ciphertext, sym(_ => ss))

  def decryptAndHash(ciphertext: ByteVector): (ByteVector, HandshakeState) =
    val (plaintext, ss) = this.symmetric.decryptAndHash(ciphertext)
    (plaintext, sym(_ => ss))

  def sym(f: SymmetricState => SymmetricState) =
    new HandshakeState(s, e, rs, re, role, patterns, f(symmetric), expected)

  def setE(newE: ECPrivateKey) =
    new HandshakeState(
      s,
      Some(newE),
      rs,
      re,
      role,
      patterns,
      symmetric,
      expected
    )

  def setRe(newRe: ECPublicKey) =
    new HandshakeState(
      s,
      e,
      rs,
      Some(newRe),
      role,
      patterns,
      symmetric,
      expected
    )

  def setRs(newRs: ECPublicKey) =
    new HandshakeState(
      s,
      e,
      Some(newRs),
      re,
      role,
      patterns,
      symmetric,
      expected
    )

  def nextPattern: Option[HandshakeState] =
    patterns.tail match
      case Nil => None
      case ps =>
        Some(
          new HandshakeState(
            s,
            e,
            rs,
            re,
            role,
            patterns.tail,
            symmetric,
            expected
          )
        )

  // def nextPattern =
  //   // if patterns.tail.isEmpty then println("@@@>>> " + symmetric.split)
  //   new HandshakeState(s, e, rs, re, role, patterns.tail, symmetric, expected)

  def nextExpected =
    new HandshakeState(s, e, rs, re, role, patterns, symmetric, expected.tail)

  val aaa: URIO[Keygen, (HandshakeState, ByteVector)] =
    UIO.succeed((this, ByteVector(0)))

  def writeMessage(
      payload: ByteVector
  ): ZIO[Keygen, HandshakeError, HandshakeResult] =
    patterns.headOption match
      case None => ???
      case Some(tokens) =>
        tokens
          .foldLeft(aaa) {
            case (s, E) =>
              s.zipWith(generateKeypair) { case ((st, buf), e) =>
                (
                  st.setE(e).mixHash(e.publicKey),
                  buf ++ e.publicKey.compressed.bytes
                )
              }
            case (s, ES) =>
              s.map { (st, buf) =>
                if st.role == HandshakeRole.Initiator then
                  (
                    st.mixKey(dh(st.e.get, st.rs.get)),
                    buf
                  )
                else
                  (
                    st.mixKey(dh(st.s, st.re.get)),
                    buf
                  )
              }
            case (s, EE) =>
              s.map { (st, buf) =>
                (
                  st.mixKey(dh(st.e.get, st.re.get)),
                  buf
                )
              }
            case (s, S) =>
              s.map { (st, buf) =>
                val (ciphertext, nextHs) =
                  st.encryptAndHash(st.s.publicKey.compressed.bytes)
                (nextHs, buf ++ ciphertext)
              }
            case (s, SE) =>
              s.map { (st, buf) =>
                if st.role == HandshakeRole.Initiator then
                  (
                    st.mixKey(dh(st.s, st.re.get)),
                    buf
                  )
                else
                  (
                    st.mixKey(dh(st.e.get, st.rs.get)),
                    buf
                  )
              }

          }
          .map { (hs, buf) =>
            val (ciphertext, nextHs) = hs.encryptAndHash(payload)
            nextHs.nextPattern match
              case None =>
                val (c1, c2) = symmetric.split
                HandshakeResult.Done(c1, c2)
              case Some(st) =>
                HandshakeResult.Continue(buf ++ ciphertext, st)
          }

  def readMessage(message: ByteVector): IO[HandshakeError, HandshakeResult] =
    val (v, (c, t)) =
      (message.head, message.tail.splitAt(message.tail.length - 16))
    patterns.headOption match
      case None => ???
      case Some(tokens) =>
        val hs = tokens.foldLeft(this) {
          case (st, E) =>
            val re = ECPublicKey.fromBytes(c)
            st.setRe(re).mixHash(re)
          case (st, EE) => st.mixKey(dh(st.e.get, st.re.get))
          case (st, ES) =>
            if st.role == HandshakeRole.Initiator then
              st.mixKey(dh(st.e.get, st.rs.get))
            else st.mixKey(dh(st.s, st.re.get))
          case (st, S) =>
            val (plaintext, nextHs) = st.decryptAndHash(c)
            nextHs.setRs(ECPublicKey.fromBytes(plaintext))
          case (st, SE) =>
            if st.role == HandshakeRole.Initiator then
              st.mixKey(dh(st.s, st.re.get))
            else st.mixKey(dh(st.e.get, st.rs.get))

        }
        val (plaintext, nextHs) = hs.decryptAndHash(t)
        UIO.succeed {
          nextHs.nextPattern match
            case None =>
              val (c1, c2) = symmetric.split
              HandshakeResult.Done(c1, c2)
            case Some(st) =>
              HandshakeResult.Continue(plaintext, st.nextExpected)
        }

object HandshakeState:
  val initsym =
    SymmetricState("Noise_XK_secp256k1_ChaChaPoly_SHA256", "lightning")
  def initiator(
      rs: ECPublicKey,
      s: ECPrivateKey
  ): HandshakeState =
    new HandshakeState(
      s = s,
      e = None,
      rs = Some(rs),
      re = None,
      HandshakeRole.Initiator,
      handshakePattern,
      initsym.mixHash(rs),
      expected = List(50, 66) // ??????
    )

  def responder(
      s: ECPrivateKey
  ): HandshakeState =
    new HandshakeState(
      s = s,
      e = None,
      rs = None,
      re = None,
      HandshakeRole.Responder,
      handshakePattern,
      initsym.mixHash(s.publicKey),
      expected = List(50, 66)
    )
end HandshakeState

def hmacHash(key: ByteVector, data: ByteVector): ByteVector =
  val mac = new HMac(new SHA256Digest())
  mac.init(new KeyParameter(key.toArray))
  mac.update(data.toArray, 0, data.length.toInt)
  val out = new Array[Byte](32)
  mac.doFinal(out, 0)
  ByteVector.view(out)

def hkdf(v1: ByteVector, v2: ByteVector): (ByteVector, ByteVector) =
  val tmp = hmacHash(v1, v2)
  val output1 = hmacHash(tmp, ByteVector(1))
  val output2 = hmacHash(tmp, output1 ++ ByteVector(2))
  (output1, output2)

def k2k(b: ByteVector): SecretKey =
  new SecretKeySpec(b.toArray, "EC")
