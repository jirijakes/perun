import perun.proto.bolt.doc.*
import org.parboiled.scala.*

val p = new Xxx { override val buildParseTree = true }

val r = ReportingParseRunner(p.Doc).run("hello ~field~ dear")

r.result

org.parboiled.support.ParseTreeUtils.printNodeTree(r)
