package perun.proto.bolt.bolt

import org.bitcoins.core.protocol.script.MultiSignatureScriptPubKey
import org.bitcoins.crypto.CryptoUtil.{doubleSHA256, sha256}
import org.typelevel.paiges.*
import scodec.bits.ByteVector
import zio.*
import zio.console.*
import zio.prelude.*
import zio.stream.*

import perun.net.rpc.*
import perun.crypto.*
import perun.crypto.secp256k1.*
import perun.p2p.*
import perun.proto.*
import perun.proto.gossip.{ChannelAnnouncement, NodeAnnouncement}
import perun.db.p2p.*

/** Specification of BOLT validation of a message `A`. See [[bolt]] for
  * details about creating bolts.
  */
class Bolt[-R, +E, A](
    val number: String,
    val name: String,
    validations: NonEmptyChunk[Val[R, E, A]]
):
  /** Perform actual validation of the given message.
    *
    * @param message decoded message
    * @param bytes encoded message
    */
  def validate(
      message: A,
      bytes: ByteVector,
      conf: perun.peer.Configuration
  ): ZIO[R, E, ZValidation[Step, Invalid, A]] =
    ZIO
      .collectAll(
        validations
          .zipWithIndexFrom(1)
          .map((valdef, index) =>
            valdef
              .validate(Context(message, bytes, conf))
              .map(v => {
                v.log(Step(index, valdef.description, v))
              })
          )
      )
      .map(_.reduce(_ &> _))

final case class Step(
    index: Int,
    description: Doc,
    result: Option[NonEmptyChunk[Invalid]]
)

object Step:
  def apply(
      index: Int,
      description: Doc,
      validation: Validation[Invalid, Any]
  ): Step =
    Step(index, description, validation.fold(e => Option(e), _ => None))

  given Document[Step] = Document.instance { case Step(index, d, res) =>
    import Doc.*
    import Style.Ansi.Fg
    import Style.Ansi.Attr

    val errors = res.map(msgs =>
      stack(msgs.map {
        case Invalid.Failure(m) =>
          (char('•') & split(m)).style(Fg.Red)
        case Invalid.Denied(m, Response.Ignore) =>
          (char('•') & split(m)).style(Fg.Yellow)
        case Invalid.Denied(m, _) =>
          (char('•') & split(m)).style(Fg.Red)
      })
    )

    res.fold(char('✓').style(Fg.Green))(_ => char('×').style(Fg.Red)) &
      text(index.toString).style(Attr.Bold ++ Fg.BrightWhite) + char('.') &
      errors.fold(d)(es => d + line + line + es).aligned
  }

/** Create new specification of BOLT validation of a message `A`. Each
  * such specification consists of one or more validations that are
  * performed in the same order as they are defined.
  *
  * ### Example
  *
  * ```scala
  * val b: Bolt[Any, Nothing, String] =
  *   bolt("#1", "String Message")(
  *     validate((msg, _) => UIO(accept(msg)), "Description")
  *   )
  * ```
  *
  * @param number BOLT number
  * @param name message name
  * @param vs comma-separated validations
  * @tparam R ZIO environment
  * @tparam E error type
  * @tparam A message type
  */
def bolt[R, E, A](number: String, name: String)(
    v1: Val[R, E, A],
    vs: Val[R, E, A]*
): Bolt[R, E, A] =
  new Bolt[R, E, A](number, name, NonEmptyChunk(v1, vs*))

case class Context[A](
    message: A,
    bytes: ByteVector,
    conf: perun.peer.Configuration
)

/** A single validation of message of `A`.
  *
  * @param validate function to perform the actual validation
  * @param description
  */
case class Val[-R, +E, A](
    validate: Context[A] => ZIO[R, E, Validation[Invalid, A]],
    description: Doc
)

/** Error type indicating failed validation.
  */
enum Invalid:

  /** Validation ended with BOLT failure and next step
    * of processing this message should be `response`.
    */
  case Denied(reason: String, response: Response)

  /** Validation ended with other type of failure.
    *
    * @param message description of error
    */
  case Failure(message: String)

enum Response:
  case Ignore
  case FailConnection
  case CloseChannel
  case Blacklist(nodes: NonEmptyChunk[NodeId])

inline def validate[R, E, A](
    validation: Context[A] => ZIO[R, E, Validation[Invalid, A]],
    description: Doc
): Val[R, E, A] = Val(validation, description)

inline def validate[R, E, A, D: Document](
    validation: Context[A] => ZIO[R, E, Validation[Invalid, A]],
    description: D
): Val[R, E, A] = validate(validation, Document[D].document(description))

inline def ignore(reason: String): Invalid =
  Invalid.Denied(reason, Response.Ignore)

inline def blacklist(reason: String, node0: NodeId, nodes: NodeId*): Invalid =
  Invalid.Denied(reason, Response.Blacklist(NonEmptyChunk(node0, nodes*)))

inline def failConnection(reason: String): Invalid =
  Invalid.Denied(reason, Response.FailConnection)

inline def accept[A](a: A): Validation[Nothing, A] = Validation.succeed(a)

/** Create a validation from pure predicate.
  */
inline def predicate[A](
    p: => Boolean,
    a: => A,
    fail: => Invalid
) = ZIO.succeed(if p then accept(a) else Validation.fail(fail))

/** Create a validation from effectful predicate.
  *
  * @param p predicate
  * @param a message to return in case of success
  * @param fail error in case of failure
  */
inline def predicateM[R, E, A0, A](m: ZIO[R, E, A0])(
    p: A0 => Boolean,
    a: => A,
    fail: => Invalid
): ZIO[R, E, Validation[Invalid, A]] =
  m.flatMap(l => predicate(p(l), a, fail))

/** Create a validation from effectful predicate.
  *
  * @param p predicate
  * @param a message to return in case of success
  * @param fail error in case of failure
  */
inline def predicateMF[R, E, A0, A](m: ZIO[R, E, A0])(
    p: A0 => Boolean,
    a: => A,
    fail: A0 => Invalid
): ZIO[R, E, Validation[Invalid, A]] =
  m.flatMap(l => predicate(p(l), a, fail(l)))

def validate(conf: perun.peer.Configuration)(
    bytes: ByteVector,
    message: Message
): ZIO[Has[P2P] & Has[Secp256k1] & Console & Has[Rpc], Throwable, ZValidation[
  Step,
  Invalid,
  Message
]] =
  import perun.proto.bolt.*

  message match
    case Message.NodeAnnouncement(m) =>
      react(nodeAnnouncement.validation, bytes, m, conf)
        .map(_.map(Message.NodeAnnouncement.apply))
    case Message.ChannelAnnouncement(m) =>
      react(channelAnnouncement.validation, bytes, m, conf)
        .map(_.map(Message.ChannelAnnouncement.apply))
    case Message.ChannelUpdate(m) =>
      react(channelUpdate.validation, bytes, m, conf)
        .map(_.map(Message.ChannelUpdate.apply))
    case m => UIO.succeed(Validation.succeed(m))

def react[R, E, A](
    bolt: Bolt[R, E, A],
    bytes: ByteVector,
    message: A,
    conf: perun.peer.Configuration
): ZIO[R & Console, E, ZValidation[Step, Invalid, A]] =
  import org.typelevel.paiges.Document.ops.given
  bolt
    .validate(message, bytes, conf)
    .tap { result =>
      putStrLn(s">>>>>>>>>> ${bolt.number} ${bolt.name} <<<<<<<<<<\n").ignore *>
        ZIO.foreach(result.getLog)(step =>
          putStrLn(step.doc.render(120)).ignore
        ) *>
        putStrLn("---------------------").ignore
    }
