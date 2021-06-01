package macros

object Bitfield:

  class Bitfield[A](n: List[String]):
    override def toString = n.mkString(" • ")
/*
  import scala.quoted.*

  case class Flax(isRed: Boolean, isDotted: Boolean)

  inline def bitfield[A](inline fs: A => Boolean*): Bitfield[A] = ${ impl[A]('fs) }

  def impl[A](fs: Expr[Seq[Any]])(using t: Type[A])(using q: Quotes): Expr[Bitfield[A]] =
    import q.reflect.*

    def names: Expr[Seq[String]] = fs match
      case Varargs(es) =>
        Expr(
          es.map(_.asTerm match {
            case Block(a, Closure(_, _)) => a.head match {
              case DefDef(_, _, _, Some(Select(_, name))) => name
            }
          }
          )
        )

    val C = TypeRepr.of[A].typeSymbol.companionModule

    // now how to call C.apply?


    '{new Bitfield[A](${names}.toList)}
 */
