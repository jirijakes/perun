package perun.proto.bolt.doc

import org.parboiled.scala.*
import org.typelevel.paiges.*

def field(s: String): Doc =
  Doc.char('`') + Doc.text(s) + Doc.char('`')

class Xxx extends Parser:
  def D = rule { zeroOrMore(Character | Field) }
  def Field = rule { "~" ~ zeroOrMore(Character) ~ "~" }
  def Character = rule { !anyOf("~") ~ ANY } 
