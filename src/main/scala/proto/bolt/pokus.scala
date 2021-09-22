package perun.proto.bolt.pokus

import org.bitcoins.core.protocol.script.MultiSignatureScriptPubKey
import org.bitcoins.crypto.CryptoUtil.{doubleSHA256, sha256}
import org.typelevel.paiges.*
import scodec.bits.ByteVector
import zio.*
import zio.prelude.*
import zio.stream.*

import perun.crypto.*
import perun.crypto.secp256k1.*
import perun.p2p.*
import perun.proto.*
import perun.proto.gossip.{ChannelAnnouncement, NodeAnnouncement}
import perun.db.p2p.*

class Bolt[-R, +E, A](
    number: String,
    name: String,
    vs: List[Val[R, E, A]]
):
  def validate(
      a: A,
      b: ByteVector
  ): ZIO[R, E, ZValidation[Nothing, Invalid, A]] =
    val x = vs.map(_.v(a, b))
    val z = ZIO.collectAll(x).map(z => z.reduce(_ &> _))
    z

class Val[-R, +E, A](
    val v: (A, ByteVector) => ZIO[R, E, Validation[Invalid, A]]
)

def bolt[R, E, A](number: String, name: String)(
    f: Val[R, E, A]*
): Bolt[R, E, A] =
  new Bolt[R, E, A](number, name, f.toList)

enum Response:
  case Ignore
  case FailConnection
  case CloseChannel

enum Invalid:
  case Denied(reason: String, response: Response)
  case Failure(message: String)

def validate[R, E, A](
    validation: (A, ByteVector) => ZIO[R, E, Validation[Invalid, A]],
    description: Doc
): Val[R, E, A] = new Val(validation)

def validate[R, E, A, D: Document](
    validation: (A, ByteVector) => ZIO[R, E, Validation[Invalid, A]],
    description: D
): Val[R, E, A] = validate(validation, Document[D].document(description))

inline def ignore(reason: String): Validation[Invalid, Nothing] =
  Validation.fail(Invalid.Denied(reason, Response.Ignore))

inline def failConnection(reason: String): Validation[Invalid, Nothing] =
  Validation.fail(Invalid.Denied(reason, Response.FailConnection))

inline def accept[A](a: A): Validation[Nothing, A] = Validation.succeed(a)

inline def predicate[A](
    p: => Boolean,
    a: => A,
    fail: => Validation[Invalid, Nothing]
): Validation[Invalid, A] = if p then accept(a) else fail

inline def predicateM[R, E, A0, A](m: ZIO[R, E, A0])(
    p: A0 => Boolean,
    a: => A,
    fail: => Validation[Invalid, Nothing]
): ZIO[R, E, Validation[Invalid, A]] = m.map(l => predicate(p(l), a, fail))

def validatePreviousChannel(
    ann: NodeAnnouncement,
    b: ByteVector
): ZIO[Has[P2P], Throwable, Validation[Invalid, NodeAnnouncement]] =
  val x = predicateM(findChannels(ann.nodeId))(_.nonEmpty, ann, ignore(""))
  val y = predicateM(findNode(ann.nodeId))(
    _.exists(_.timestamp < ann.timestamp),
    ann,
    ignore("")
  )

  x.zipWithPar(y)(_ &> _)

def validateSignature(
    ann: NodeAnnouncement,
    b: ByteVector
): URIO[Has[Secp256k1], Validation[Invalid, NodeAnnouncement]] =
  predicateM(
    verifySignature(
      ann.signature,
      doubleSHA256(b.drop(2 + 64)),
      ann.nodeId
    )
  )(_ == true, ann, failConnection(""))

def vvv(
    s: String
)(in: String, b: ByteVector): UIO[Validation[Invalid, String]] = UIO(
  ignore("Hovno " + s)
) //UIO(accept(s))

def field(s: String): Doc =
  Doc.text(s).style(Style.Ansi.Fg.Yellow ++ Style.Ansi.Attr.Bold)

def aaa: Bolt[Any, Nothing, String] =
  import Doc.*

  bolt("#7", "Node announcement")(
    validate(
      vvv("A"),
      text("if") & field("signature") & split(
        "is NOT a valid signature (using"
      ) & field("node_id") & split(
        "of the double-SHA256 of the entire message following the"
      ) & field("signature") & split(
        "field, including any future fields appended to the end)"
      ) + char(':') /
        (text("SHOULD fail the connection.") /
          text("MUST NOT process the message further.")).indent(3)
    ),
    validate(
      vvv("B"),
      "if ~node_id~ is NOT previously known from a ~channel_announcement~ message, OR if ~timestamp~ is NOT greater than the last-received ~node_announcement~ from this ~node_id~: SHOULD ignore the message."
    )
  )

def bbb: Bolt[Has[P2P] & Has[Secp256k1], Throwable, NodeAnnouncement] =
  import Doc.*

  bolt("#7", "Node announcement")(
    validate(
      validateSignature,
      text("if") & field("signature") & split(
        "is NOT a valid signature (using"
      ) & field("node_id") & split(
        "of the double-SHA256 of the entire message following the"
      ) & field("signature") & split(
        "field, including any future fields appended to the end)"
      ) + char(':') /
        (text("SHOULD fail the connection.") /
          text("MUST NOT process the message further.")).indent(3)
    ),
    validate(
      validatePreviousChannel,
      "if ~node_id~ is NOT previously known from a ~channel_announcement~ message, OR if ~timestamp~ is NOT greater than the last-received ~node_announcement~ from this ~node_id~: SHOULD ignore the message."
    )
  )

// def xxx: ZIO[Has[P2P] & Has[Secp256k1], Invalid, NodeAnnouncement] =
//   bbb.validate(???, ???)

object Pokus extends App:
  import Doc.*
  def run(args: List[String]) = aaa
    .validate("", ByteVector.empty)
    // .either
    .flatMap(x => console.putStrLn(x.toString))
    .exitCode

  def run2(args: List[String]) = console
    .putStrLn(
      (text("if") & field("signature") & split(
        "is NOT a valid signature (using"
      ) & field("node_id") & split(
        "of the double-SHA256 of the entire message following the"
      ) & field("signature") & split(
        "field, including any future fields appended to the end)"
      ) + char(':') /
        (text("SHOULD fail the connection.") /
          text("MUST NOT process the message further.")).indent(3)).render(80)
    )
    .exitCode
