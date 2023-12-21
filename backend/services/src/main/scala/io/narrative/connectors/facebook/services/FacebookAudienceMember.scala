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
    maid: Option[String] = None
)

object FacebookAudienceMember {
  sealed trait Field
  object Field {
    implicit val ordering: Ordering[Field] = Ordering.fromLessThan((a, b) => ordinal(a) < ordinal(b))

    private def ordinal(field: Field): Int = field match {
      case BirthDay         => 1
      case BirthMonth       => 2
      case BirthYear        => 3
      case Country          => 4
      case Email            => 5
      case FirstName        => 6
      case FirstNameInitial => 7
      case Gender           => 8
      case LastName         => 9
      case MobileAdId       => 10
    }
  }
  case object BirthDay extends Field
  case object BirthMonth extends Field
  case object BirthYear extends Field
  case object Country extends Field
  case object Email extends Field
  case object FirstName extends Field
  case object FirstNameInitial extends Field
  case object Gender extends Field
  case object LastName extends Field
  case object MobileAdId extends Field

  def headerValue(field: Field): String = field match {
    case BirthDay         => "DOBD"
    case BirthMonth       => "DOBM"
    case BirthYear        => "DOBY"
    case Country          => "COUNTRY"
    case Email            => "EMAIL"
    case FirstName        => "FN"
    case FirstNameInitial => "FI"
    case Gender           => "GEN"
    case LastName         => "LN"
    case MobileAdId       => "MADID"
  }

  /** The audience member as an array of values suitable for passing to the API.
    *
    * NB: The Facebook Custom Audience upload UI client is inconsistent about what to do with missing values: it
    * sometimes SHA-256 hashes the empty string ("") and sometimes sends an empty string. We choose to do the latter.
    */
  def fieldValues(member: FacebookAudienceMember, fields: List[Field]): List[String] = fields.map {
    case FacebookAudienceMember.BirthDay         => member.birthDay.getOrElse("")
    case FacebookAudienceMember.BirthMonth       => member.birthMonth.getOrElse("")
    case FacebookAudienceMember.BirthYear        => member.birthYear.getOrElse("")
    case FacebookAudienceMember.Country          => member.country.getOrElse("")
    case FacebookAudienceMember.Email            => member.email.getOrElse("")
    case FacebookAudienceMember.FirstName        => member.firstName.getOrElse("")
    case FacebookAudienceMember.FirstNameInitial => member.firstNameInitial.getOrElse("")
    case FacebookAudienceMember.Gender           => member.gender.getOrElse("")
    case FacebookAudienceMember.LastName         => member.lastName.getOrElse("")
    case FacebookAudienceMember.MobileAdId       => member.maid.getOrElse("")
  }

  def nonEmptyFields(members: List[FacebookAudienceMember]): Set[Field] =
    members.foldLeft(Set.empty[Field])((acc, member) => acc ++ nonEmptyFields(member))

  def nonEmptyFields(member: FacebookAudienceMember): List[Field] = List(
    member.birthDay.map(_ => BirthDay),
    member.birthMonth.map(_ => BirthMonth),
    member.birthYear.map(_ => BirthYear),
    member.country.map(_ => Country),
    member.email.map(_ => Email),
    member.firstName.map(_ => FirstName),
    member.firstNameInitial.map(_ => FirstNameInitial),
    member.gender.map(_ => Gender),
    member.lastName.map(_ => LastName),
    member.maid.map(_ => MobileAdId)
  ).flatten
}
