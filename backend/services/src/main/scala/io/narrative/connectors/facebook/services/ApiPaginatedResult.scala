package io.narrative.connectors.facebook.services

import cats.{Eq, Show}
import cats.instances.list._
import cats.syntax.eq._
import cats.syntax.show._
import io.circe.{Decoder, Encoder, Json}
import io.circe.syntax._

final case class ApiPaginatedResult[T](records: List[T]) extends AnyVal
object ApiPaginatedResult {
  implicit def encoder[T: Encoder]: Encoder[ApiPaginatedResult[T]] =
    Encoder.instance(res => Json.obj("records" -> res.records.asJson))

  implicit def decoder[T: Decoder]: Decoder[ApiPaginatedResult[T]] =
    Decoder.instance(cursor => cursor.downField("records").as[List[T]].map(ApiPaginatedResult(_)))

  implicit def eq[T: Eq]: Eq[ApiPaginatedResult[T]] = Eq.instance((a, b) => a.records === b.records)
  implicit def show[T: Show]: Show[ApiPaginatedResult[T]] = Show.show(result => result.records.show)
}
