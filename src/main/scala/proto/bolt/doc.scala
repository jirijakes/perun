package perun.proto.bolt.doc

import cats.parse.{Parser0, Parser as P, Numbers}
import org.typelevel.paiges.*
import cats.data.NonEmptyList

def field(s: String): Doc =
  Doc.char('`') + Doc.text(s) + Doc.char('`')

enum D:
  case Plain(t: String)
  case Code(t: String)
  case Must
  case Should
  case May
  case Not

val parser =
  val whitespace = P.charIn(" ").void
  val whitespaces = whitespace.rep.void
  val whitespaces0 = whitespaces.rep0.void
  val newLine = P.charIn("\r\n").void
  val word = P.charIn('a' to 'z').rep.string
  val plain = word.map(s => D.Plain(s))
  val field = word.surroundedBy(P.char('~')).map(s => D.Code(s))

  whitespaces0.with1 *> (P
    .oneOf(plain :: field :: Nil))
    .repSep(whitespaces) <* (whitespaces0 ~ P.end)

import org.parboiled2.*

class Prd(val input: ParserInput) extends Parser:
  def haha = rule { oneOrMore(prd).separatedBy(space) ~ EOI }

  def space = rule(oneOrMore(' '))

  def prd = rule { allow | field | text }

  def field = rule { '~' ~ capture(ident) ~> (D.Code(_)) ~ '~' }

  def ident = rule { oneOrMore(CharPredicate.Alpha | '_') }

  def text = rule {
    capture(
      oneOrMore(CharPredicate.AlphaNum | ',' | '(' | ')' | '-' | ':')
    ) ~> (D.Plain(_))
  }

  def allow = rule(must | may | should | not)

  def must = rule("MUST" ~ push(D.Must))
  def may = rule("MAY" ~ push(D.May))
  def should = rule("SHOULD" ~ push(D.Should))
  def not = rule("NOT" ~ push(D.Not))
