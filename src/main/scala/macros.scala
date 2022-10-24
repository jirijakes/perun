package perun.macros

import scala.quoted.*

inline def enumValues[E <: scala.reflect.Enum]: Array[E] =
  ${ enumValuesImpl[E] }

private def enumValuesImpl[E](using t: Type[E])(using Quotes): Expr[Array[E]] =
  import quotes.reflect.*
  val a = TypeTree.of[E]
  val s = a.symbol
  val o = s.companionModule
  val comp = Ref(o)
  Select.unique(comp, "values").asExprOf[Array[E]]
