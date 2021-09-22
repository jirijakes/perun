package perun.proto.bolt.doc

import cats.parse.{Parser0, Parser as P, Numbers}
import org.typelevel.paiges.*
import cats.data.NonEmptyList

def field(s: String): Doc =
  Doc.char('`') + Doc.text(s) + Doc.char('`')

enum D:
  case Plain(t: String)
  case Code(t: String)

val parser = {
  val whitespace = P.charIn(" ").void
  val whitespaces = whitespace.rep.void
  val whitespaces0 = whitespaces.rep0.void
  val newLine = P.charIn("\r\n").void
  val word = P.charIn('a' to 'z').rep.string
  val plain = word.map(s => D.Plain(s))
  val field = word.surroundedBy(P.char('~')).map(s => D.Code(s))

  whitespaces0.with1 *> (P.oneOf(plain :: field :: Nil)).repSep(whitespaces) <* (whitespaces0 ~ P.end)

}
