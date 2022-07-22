package io.narrative.connectors.facebook.services

/** An audience member deliverable to a Facebook custom audience.
  *
  * @see
  *   https://developers.facebook.com/docs/marketing-api/audiences/guides/custom-audiences/
  *
  * @param birthDay
  *   SHA-256 hashed day of birth. Hashed. Facebook schema field: DOBD
  *
  * @param birthMonth
  *   SHA-256 hashed month of birth. Hashed. Facebook schema field: DOBM
  *
  * @param birthYear
  *   SHA-256 hashed year of birth. Facebook schema field: DOBY
  *
  * @param country
  *   SHA-256 hashed country code. Facebook schema field: COUNTRY
  *
  * @param email
  *   SHA-256 hashed email address. Facebook schema field: EMAIL
  *
  * @param firstName
  *   SHA-256 hashed first name. Facebook schema field: FN
  *
  * @param firstNameInitial
  *   SHA-256 hashed first initial of first name. Facebook schema field: FI
  *
  * @param gender
  *   SHA-256 hashed gender. Facebook schema field: GEN
  *
  * @param lastName
  *   SHA-256 hashed last name. Facebook schema field: LN
  *
  * @param lastNameInitial
  *   SHA-256 hashed first initial of last name. Facebook schema field: LI
  *
  * @param maid
  *   Mobile advertiser ID. Facebook schema field: MADID
  */
final case class FacebookAudienceMember(
    birthDay: Option[String] = None,
    birthMonth: Option[String] = None,
    birthYear: Option[String] = None,
    country: Option[String] = None,
    email: Option[String] = None,
    firstName: Option[String] = None,
    firstNameInitial: Option[String] = None,
    gender: Option[String] = None,
    lastName: Option[String] = None,
    lastNameInitial: Option[String] = None,
    maid: Option[String] = None
) {

  /** The audience member as an array of values suitable for passing to the API. Facebook's custom audience file upload
    *
    * **NB**: value order must match the declared order in [[FacebookAudienceMember.header]]
    *
    * UI client is inconsistent about what to do with missing values: it sometimes SHA-256 hashes the empty string ("")
    * or sometimes sends an empty string. We choose to do the latter.
    */
  lazy val values: List[String] = List(
    birthDay.getOrElse(""),
    birthMonth.getOrElse(""),
    birthYear.getOrElse(""),
    country.getOrElse(""),
    email.getOrElse(""),
    firstName.getOrElse(""),
    firstNameInitial.getOrElse(""),
    gender.getOrElse(""),
    lastName.getOrElse(""),
    lastNameInitial.getOrElse(""),
    maid.getOrElse("")
  )
}

object FacebookAudienceMember {
  val header: List[String] = List(
    "DOBD",
    "DOBM",
    "DOBY",
    "COUNTRY",
    "EMAIL",
    "FN",
    "FI",
    "GEN",
    "LN",
    "LI",
    "MADID"
  )
}
