package perun.crypto.keygen

import zio.*
import zio.prelude.NonEmptyList

import perun.crypto.PrivateKey

trait Keygen:
  def generateKeypair: UIO[PrivateKey]

/** Creates Keygen which generates truly random keys.
  *
  * @example
  *
  * When new key is requested inside `prg`, random keys will be always returned.
  *
  * ```
  * val prg: URIO[Keygen, Unit] = ???
  *
  * def run(args: List[String]) =
  *   prg
  *     .provideLayer(live)
  *     .exitCode
  * ```
  */
val liveKeygen: ULayer[Keygen] =
  ZLayer.succeed(
    new Keygen:
      def generateKeypair: UIO[PrivateKey] = PrivateKey.freshPrivateKey
  )

/** Creates Keygen which returns the same keys in the same order ad infinitum.
  * The listed keys must be valid 32-byte hex strings, otherwise disaster
  * happens.
  *
  * @example
  *
  * When new key is requested inside `prg`, only the two provided keys will be
  * returned one after another.
  *
  * ```
  * val prg: URIO[Keygen, Unit] = ???
  *
  * def run(args: List[String]) =
  *   prg
  *     .provideLayer(repeat("0101…", "0202…"))
  *     .exitCode
  * ```
  */
def repeat(key0: String, other: String*): ULayer[Keygen] =
  val nel = NonEmptyList.fromIterable(key0, other)
  ZLayer {
    Ref
      .make(nel)
      .map { ref =>
        new Keygen:
          def generateKeypair: UIO[PrivateKey] =
            ref.modify {
              case NonEmptyList.Single(h)  => (PrivateKey.fromHex(h), nel)
              case NonEmptyList.Cons(h, t) => (PrivateKey.fromHex(h), t)
            }

      }
  }

def generateKeypair: URIO[Keygen, PrivateKey] =
  ZIO.serviceWithZIO(_.generateKeypair)
