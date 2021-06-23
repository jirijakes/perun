package perun.proto.bolt.doc

import org.parboiled.scala.*
import org.typelevel.paiges.*

def field(s: String): Doc =
  Doc.char('`') + Doc.text(s) + Doc.char('`')

class Xxx extends Parser:
  def Doc = rule { WhiteSpace ~ zeroOrMore(Word | Field) ~ WhiteSpace }
  def Field = rule { "~" ~ oneOrMore(Word) ~ "~" }
  def Word = rule { oneOrMore(Character) }
  def Character = rule { !anyOf("~") ~ ANY } 
  def Number = rule { oneOrMore("0" - "9") }
  def WhiteSpace: Rule0 = rule { zeroOrMore(anyOf(" \n\r\t\f")) }
