package perun.proto.bolt.doc

import org.typelevel.paiges.*

def field(s: String): Doc =
  Doc.text(s).style(Style.Ansi.Fg.Yellow ++ Style.Ansi.Attr.Bold)
