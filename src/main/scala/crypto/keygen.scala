package crypto.keygen

import org.bitcoins.crypto.ECPrivateKey
import zio.*
import zio.prelude.NonEmptyList

type Keygen = Has[Service]

trait Service:
  def generateKeypair: UIO[ECPrivateKey]

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
val live: ULayer[Keygen] =
  ZLayer.succeed(
    new Service:
      def generateKeypair: UIO[ECPrivateKey] =
        UIO.effectTotal(ECPrivateKey.freshPrivateKey)
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
  Ref
    .make(nel)
    .map { ref =>
      new Service:
        def generateKeypair: UIO[ECPrivateKey] =
          ref.modify {
            case NonEmptyList.Single(h)  => (ECPrivateKey.fromHex(h), nel)
            case NonEmptyList.Cons(h, t) => (ECPrivateKey.fromHex(h), t)
          }

    }
    .toLayer

def generateKeypair: URIO[Keygen, ECPrivateKey] =
  ZIO.accessM(x => x.get.generateKeypair)
