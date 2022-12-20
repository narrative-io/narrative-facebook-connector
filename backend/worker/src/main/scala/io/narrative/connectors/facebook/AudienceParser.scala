package io.narrative.connectors.facebook

import cats.syntax.either._
import cats.syntax.option._
import io.circe.{ACursor, Json}
import io.narrative.connectors.facebook.services.FacebookAudienceMember

import java.time.{Instant, LocalDateTime, ZoneOffset}
import java.util.Locale

object AudienceParser {

  // Datasets don't have their fields nested inside "data", like legacy subscriptions do.
  def parseLegacy(in: Json): FacebookAudienceMember = parse((in: Json) => in.hcursor.downField("data"))(in)
  def parseDataset(in: Json): FacebookAudienceMember = parse(_.hcursor)(in)

  def parse(downFn: Json => ACursor)(in: Json): FacebookAudienceMember = {
    val data = downFn(in)
    val birthInfo = parseBirthInfo(data)
    val name = parseName(data)
    FacebookAudienceMember(
      birthDay = birthInfo.day,
      birthMonth = birthInfo.month,
      birthYear = birthInfo.year,
      country = parseCountry(data),
      email = parseHem(data),
      firstName = name.first,
      firstNameInitial = name.firstInitial,
      gender = parseGender(data),
      lastName = name.last,
      maid = parseMaid(data)
    )
  }

  // NB: fields are SHA-256 hashed per Facebook Custom Audience requirements
  private final case class BirthInfo(year: Option[String], month: Option[String], day: Option[String])

  // NB: fields are SHA-256 hashed per Facebook Custom Audience requirements
  private final case class NameInfo(
      first: Option[String],
      firstInitial: Option[String],
      last: Option[String]
  )

  private def parseBirthInfo(data: ACursor): BirthInfo = {
    val birthdate =
      data
        .get[String]("birthdate")
        .toOption
        .flatMap(ts => Either.catchNonFatal(Instant.parse(ts)).toOption)
        .map(LocalDateTime.ofInstant(_, ZoneOffset.UTC))
    val year =
      birthdate.map(_.getYear.toString).orElse(data.get[Long]("birth_year").toOption.map(_.toString)).map(sha256)
    val month = birthdate.map(_.getMonth.getValue).map(m => f"${m}%02d").map(sha256)
    val day = birthdate.map(_.getDayOfMonth).map(d => f"${d}%02d").map(sha256)
    BirthInfo(year = year, month = month, day = day)
  }

  private def parseCountry(data: ACursor): Option[String] =
    data.get[String]("iso_3166_1_country").toOption.map(_.toLowerCase(Locale.ENGLISH)).map(sha256)

  private def parseGender(data: ACursor): Option[String] =
    data.downField("hl7_gender").get[String]("gender").toOption match {
      case Some("male")   => sha256("m").some
      case Some("female") => sha256("f").some
      case _              => none // Facebook only accepts gender values of "male" or "female" ಠ_ಠ
    }

  private def parseName(data: ACursor): NameInfo = {
    val field = data.downField("person_name")
    // Facebook's official guidance wrt how to format names:
    // "Use a-z only. Lowercase only, no punctuation. Special characters in UTF-8 format."
    // 'Use a-z only' would seem to contradict 'Special character in UTF-8 format', so we just lowercase using the
    // standard "english" local.
    val first = field.get[String]("given_name").toOption.map(_.toLowerCase(Locale.ENGLISH))
    val last = field.get[String]("family_name").toOption.map(_.toLowerCase(Locale.ENGLISH))
    NameInfo(
      first = first.map(sha256),
      firstInitial = first.flatMap(_.headOption).map(_.toString).map(sha256),
      last = last.map(sha256)
    )
  }

  private def parseHem(data: ACursor): Option[String] =
    hemFromUniqueId(data)
      .orElse(hemFromHem(data))
      .orElse(hemFromSha256(data))

  private def hemFromHem(data: ACursor): Option[String] =
    data.downField("hashed_email").get[String]("type").toOption match {
      case Some("sha256_email") => data.downField("hashed_email").get[String]("value").toOption
      case _                    => none
    }

  private def hemFromSha256(data: ACursor): Option[String] =
    data.downField("sha256_hashed_email").get[String]("value").toOption

  private def hemFromUniqueId(data: ACursor): Option[String] =
    data.downField("unique_id").get[String]("type").toOption match {
      case Some("sha256_email") => data.downField("unique_id").get[String]("value").toOption
      case _                    => none
    }

  private def parseMaid(data: ACursor): Option[String] =
    maidFromUniqueId(data)
      .orElse(maidFromMaid(data))
      .orElse(maidFromAdid(data))
      .orElse(maidFromIdfa(data))

  private def maidFromAdid(data: ACursor): Option[String] =
    data.downField("android_advertising_id").get[String]("value").toOption

  private def maidFromIdfa(data: ACursor): Option[String] =
    data.downField("apple_idfa").get[String]("value").toOption

  private def maidFromMaid(data: ACursor): Option[String] =
    data.downField("mobile_id_unique_identifier").get[String]("value").toOption

  private def maidFromUniqueId(data: ACursor): Option[String] =
    data.downField("unique_id").get[String]("type").toOption match {
      case Some("adid") => data.downField("unique_id").get[String]("value").toOption
      case Some("idfa") => data.downField("unique_id").get[String]("value").toOption
      case Some(_)      => none
      // absent an id type, assume it's a MAID
      case None => data.downField("unique_id").get[String]("value").toOption
    }

  private def sha256(text: String): String = String.format(
    "%064x",
    new java.math.BigInteger(1, java.security.MessageDigest.getInstance("SHA-256").digest(text.getBytes("UTF-8")))
  )
}
