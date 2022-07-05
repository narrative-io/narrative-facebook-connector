package io.narrative.connectors.facebook.meta

import cats.syntax.either._
import com.typesafe.scalalogging.LazyLogging
import doobie.Meta
import io.circe
import io.circe.{Decoder, Encoder, Json}
import io.circe.parser.parse
import io.circe.syntax._
import org.postgresql.util.PGobject

import scala.reflect.runtime.universe._

object JsonMeta extends LazyLogging {
  implicit val jsonMeta: Meta[Json] =
    Meta[String].imap(json => parse(json).leftMap[Json](e => throw e).merge)(_.deepDropNullValues.noSpacesSortKeys)

  def jsonbMeta[T: Decoder: Encoder](implicit tag: TypeTag[T]): Meta[T] =
    Meta.Advanced
      .other[PGobject]("json")
      .timap[T] { a =>
        circe.jawn
          .parse(a.getValue)
          .flatMap(_.as[T])
          .leftMap[T] { e =>
            logger.error(
              s"Failed to decode JSON value from database to expected type ${tag}. Raw PGobject value: ${a.getValue}",
              e
            )
            throw e
          }
          .merge
      }(a => {
        val o = new PGobject
        o.setType("json")
        o.setValue(a.asJson.noSpaces)
        o
      })
}
