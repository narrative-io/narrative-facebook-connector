package io.narrative.connectors.facebook.domain

import cats.{Eq, Show}
import doobie.Meta
import io.circe.{Decoder, Encoder}

import java.time.Instant

/** Settings for a particular delivery (e.g. a profile + subscription pair) that may be generated and not chosen by the
  * client in either their profile or quick settings.
  *
  * Canonical use case: for the first delivery of a new (profile_id, subscription_id) pair, the connector may have to
  * create a custom audience on the user's behalf. The id of the created audience needs to be persisted and associated
  * with the (profile_id, subscription_id) pair so that future deliveries know which audience to deliver to.
  */
final case class Settings(id: Settings.Id, audienceId: Audience.Id, createdAt: Instant, updatedAt: Instant)

object Settings {
  final case class Id(value: String) extends AnyVal
  object Id {
    implicit val decoder: Decoder[Id] = Decoder.decodeString.map(Id.apply)
    implicit val encoder: Encoder[Id] = Encoder.encodeString.contramap(_.value)
    implicit val eq: Eq[Id] = Eq.fromUniversalEquals
    implicit val meta: Meta[Id] = Meta[String].imap(Id.apply)(_.value)
    implicit val show: Show[Id] = Show.show(_.value)

    def fromSubscription(profileId: Profile.Id, subscriptionId: SubscriptionId): Id =
      new Id(s"profile_${profileId.value}:subscription_${subscriptionId.value}")

    def fromConnection(profileId: Profile.Id, connectionId: ConnectionId): Id =
      new Id(s"profile_${profileId.value}:connection_${connectionId.value}")
  }
}
