package io.narrative.connectors.facebook.services

/** An audience member deliverable to a Facebook custom audience.
  *
  * @see
  *   https://developers.facebook.com/docs/marketing-api/audiences/guides/custom-audiences/
  *
  * The MVP implementation only supports mobile advertisement IDs.
  *
  * @param maid
  *   Mobile advertiser ID. Facebook schema field: MADID
  */
case class FacebookAudienceMember(maid: Option[String])
