package noise

// import fr.acinq.secp256k1.Secp256k1
import org.bitcoins.crypto.CryptoUtil.sha256
import org.bitcoins.crypto.*
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.macs.HMac
import org.bouncycastle.crypto.params.KeyParameter
import scodec.bits.ByteVector
import zio.*

import perun.crypto.keygen.*
import perun.crypto.secp256k1.*
import perun.crypto.{DecryptionError, chacha}
import perun.proto.codecs.*

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
  given Binary[PublicKey] = _.publicKey.compressed.bytes

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
  case Running(ck: ByteVector, k: ByteVector, n: BigInt)

  def next: CipherState = this match
    case Empty => Empty
    case Running(ck, k, 999) =>
      val (ck1, k1) = hkdf(ck, k)
      Running(ck1, k1, 0)
    case Running(ck, k, n) => Running(ck, k, n + 1)

  def encryptWithAd(
      ad: ByteVector,
      plaintext: ByteVector
  ): (ByteVector, CipherState) = this match
    case Empty             => (plaintext, Empty)
    case Running(ck, k, n) => (chacha.encryptPad(k, n, ad, plaintext), next)

  def decryptWithAd0(ciphertext: ByteVector) =
    decryptWithAd(ByteVector.empty, ciphertext)

  def decryptWithAd(
      ad: ByteVector,
      ciphertext: ByteVector
  ): Either[DecryptionError, (ByteVector, CipherState)] = this match
    case Empty => Right((ciphertext, Empty))
    case Running(ck, k, n) =>
      chacha.decryptPad(k, n, ad, ciphertext).map((_, next))

final class SymmetricState(
    ck: ByteVector,
    h: ByteVector,
    cipher: CipherState
):
  def mixHash[B: Binary](data: B): SymmetricState =
    new SymmetricState(ck, sha256(h ++ data.bytes).bytes, cipher)

  def mixKey[B: Binary](data: B): SymmetricState =
    val (ck1, temp) = hkdf(ck, data.bytes)
    new SymmetricState(ck1, h, CipherState.Running(ck, temp, 0))

  def cip(newCipherState: CipherState): SymmetricState =
    new SymmetricState(ck, h, newCipherState)

  def encryptAndHash(plaintext: ByteVector): (ByteVector, SymmetricState) =
    val (ciphertext, nextCipher) = cipher.encryptWithAd(h, plaintext)
    (ciphertext, mixHash(ciphertext).cip(nextCipher))

  def decryptAndHash(
      ciphertext: ByteVector
  ): Either[DecryptionError, (ByteVector, SymmetricState)] =
    cipher
      .decryptWithAd(h, ciphertext)
      .map((plaintext, nextCipher) =>
        (plaintext, mixHash(ciphertext).cip(nextCipher))
      )

  def split: (CipherState, CipherState) =
    val (tempk1, tempk2) = hkdf(ck, ByteVector.empty)
    (CipherState.Running(ck, tempk1, 0), CipherState.Running(ck, tempk2, 0))

object SymmetricState:
  def apply(protocolName: String, prologue: String): SymmetricState =
    val ck = sha256(protocolName).bytes
    new SymmetricState(ck, ck, CipherState.Empty).mixHash(prologue)
  end apply

// def dh(v1: ECPrivateKey, v2: ECPublicKey) =
//   ByteVector.view(Secp256k1.get().ecdh(v1.bytes.toArray, v2.bytes.toArray))

enum HandshakeResult:
  case Continue(m: ByteVector, st: HandshakeState)
  case Done(c1: CipherState, c2: CipherState)

final class HandshakeState(
    val s: PrivateKey,
    val e: Option[PrivateKey],
    val rs: Option[PublicKey],
    val re: Option[PublicKey],
    val role: HandshakeRole,
    patterns: List[List[Token]],
    val symmetric: SymmetricState,
    val expected: List[Int]
):

  def mixHash[B: Binary](data: B): HandshakeState =
    sym(_.mixHash(data))

  def mixKey[B: Binary](data: B): HandshakeState =
    sym(_.mixKey(data))

  def encryptAndHash(plaintext: ByteVector): (ByteVector, HandshakeState) =
    val (ciphertext, ss) = this.symmetric.encryptAndHash(plaintext)
    (ciphertext, sym(_ => ss))

  def decryptAndHash(
      ciphertext: ByteVector
  ): Either[DecryptionError, (ByteVector, HandshakeState)] =
    this.symmetric
      .decryptAndHash(ciphertext)
      .map((plaintext, ss) => (plaintext, sym(_ => ss)))

  def sym(f: SymmetricState => SymmetricState) =
    new HandshakeState(s, e, rs, re, role, patterns, f(symmetric), expected)

  def setE(newE: PrivateKey) =
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

  def setRe(newRe: PublicKey) =
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

  def setRs(newRs: PublicKey) =
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

  val aaa: URIO[Has[Keygen], (HandshakeState, ByteVector)] =
    UIO.succeed((this, ByteVector(0)))

  def writeMessage(
      payload: ByteVector
  ): ZIO[Has[Keygen] & Has[Secp256k1], HandshakeError, HandshakeResult] =
    ZIO.environment[Has[Secp256k1]].map(_.get).flatMap { secp =>
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
                      st.mixKey(secp.ecdh(st.e.get, st.rs.get)),
                      buf
                    )
                  else
                    (
                      st.mixKey(secp.ecdh(st.s, st.re.get)),
                      buf
                    )
                }
              case (s, EE) =>
                s.map { (st, buf) =>
                  (
                    st.mixKey(secp.ecdh(st.e.get, st.re.get)),
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
                      st.mixKey(secp.ecdh(st.s, st.re.get)),
                      buf
                    )
                  else
                    (
                      st.mixKey(secp.ecdh(st.e.get, st.rs.get)),
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
    }

  def readMessage(
      message: ByteVector
  ): ZIO[Has[Secp256k1], HandshakeError, HandshakeResult] =
    val (v, (c, t)) =
      (message.head, message.tail.splitAt(message.tail.length - 16))
    patterns.headOption match
      case None => ???
      case Some(tokens) =>
        ZIO.environment[Has[Secp256k1]].map(_.get).flatMap { secp =>
          val hs = tokens.foldLeft[Either[DecryptionError, HandshakeState]](
            Right(this)
          ) {
            case (Right(st), E) =>
              val re = PublicKey.fromBytes(c)
              Right(st.setRe(re).mixHash(re))
            case (Right(st), EE) =>
              Right(st.mixKey(secp.ecdh(st.e.get, st.re.get)))
            case (Right(st), ES) =>
              if st.role == HandshakeRole.Initiator then
                Right(st.mixKey(secp.ecdh(st.e.get, st.rs.get)))
              else Right(st.mixKey(secp.ecdh(st.s, st.re.get)))
            case (Right(st), S) =>
              st.decryptAndHash(c)
                .map((plaintext, nextHs) =>
                  nextHs.setRs(PublicKey.fromBytes(plaintext))
                )
            case (Right(st), SE) =>
              if st.role == HandshakeRole.Initiator then
                Right(st.mixKey(secp.ecdh(st.s, st.re.get)))
              else Right(st.mixKey(secp.ecdh(st.e.get, st.rs.get)))
            case (left, _) => left

          }
          hs.flatMap(_.decryptAndHash(t)) match
            case Right((plaintext, nextHs)) =>
              UIO.succeed {
                nextHs.nextPattern match
                  case None =>
                    val (c1, c2) = nextHs.symmetric.split
                    HandshakeResult.Done(c1, c2)
                  case Some(st) =>
                    HandshakeResult.Continue(plaintext, st.nextExpected)
              }

            case Left(DecryptionError.BadTag) =>
              IO.fail(HandshakeError.InvalidCiphertext)
        }

object HandshakeState:
  val initsym =
    SymmetricState("Noise_XK_secp256k1_ChaChaPoly_SHA256", "lightning")
  def initiator(
      rs: PublicKey,
      s: PrivateKey
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
      s: PrivateKey
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
