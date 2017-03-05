package com.outr.arango

import scala.annotation.compileTimeOnly
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.experimental.macros
import scala.reflect.macros.blackbox
import scala.concurrent.ExecutionContext.Implicits.global

@compileTimeOnly("Enable macro paradise to expand compile-time macros")
object Macros {
  def aql(c: blackbox.Context)(args: c.Expr[Any]*): c.Expr[Query] = {
    import c.universe._

    c.prefix.tree match {
      case Apply(_, List(Apply(_, rawParts))) => {
        val parts = rawParts map { case t @ Literal(Constant(const: String)) => (const, t.pos) }

        val b = new StringBuilder
        parts.zipWithIndex.foreach {
          case ((raw, _), index) => {
            if (index > 0) {
              b.append(s"@arg$index")
            }
            b.append(raw)
          }
        }
        val argsMap = args.zipWithIndex.map {
          case (value, index) => {
            val vt = value.actualType
            val queryArg = if (vt <:< typeOf[String]) {
              c.Expr[QueryArg](q"com.outr.arango.QueryArg.string($value)")
            } else if (vt <:< typeOf[Int]) {
              c.Expr[QueryArg](q"com.outr.arango.QueryArg.int($value)")
            } else {
              c.abort(c.enclosingPosition, s"Unsupported QueryArg: $vt.")
            }
            s"arg${index + 1}" -> queryArg
          }
        }.toMap

        val query = b.toString().trim
        val future = ArangoSession.default.flatMap { session =>
          val result = session.parse(query)
          result.onComplete { _ =>
            session.instance.dispose()
          }
          result
        }

        val result = Await.result(future, 30.seconds)
        if (result.error) {
          c.abort(c.enclosingPosition, s"Error #${result.code}. Bad syntax for AQL query: $query.")
        }
        c.Expr[Query](q"""com.outr.arango.Query($query, $argsMap)""")
      }
      case _ => c.abort(c.enclosingPosition, "Bad usage of cypher interpolation.")
    }
  }
}