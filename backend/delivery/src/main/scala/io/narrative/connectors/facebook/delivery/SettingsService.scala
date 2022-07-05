package io.narrative.connectors.facebook.delivery

import cats.data.{NonEmptyList, OptionT}
import cats.effect.IO
import cats.syntax.option.none
import cats.syntax.show._
import com.typesafe.scalalogging.LazyLogging
import io.narrative.connectors.facebook.domain.Job.CommandPayload
import io.narrative.connectors.facebook.domain.Profile.QuickSettings
import io.narrative.connectors.facebook.domain.{AdAccount, Audience, Job, Profile, Settings}
import io.narrative.connectors.facebook.services.{
  AudienceResponse,
  FacebookClient,
  FacebookToken,
  TokenEncryptionService
}
import io.narrative.connectors.facebook.stores.{ProfileStore, SettingsStore}

// todo(mbabic)
// - check token validity
// - return error if audience does not exist
// - return error if audience cannot be created
// - grab global lock by settings id to prevent duplicate audience creation (not important for MVP)
class SettingsService(
    encryption: TokenEncryptionService.Ops[IO],
    fb: FacebookClient.Ops[IO],
    profileStore: ProfileStore.Ops[IO],
    settingsStore: SettingsStore.Ops[IO]
) extends SettingsService.Ops[IO]
    with LazyLogging {
  import SettingsService._

  override def getOrCreate(
      quickSettings: Option[QuickSettings],
      payload: CommandPayload,
      profileId: Profile.Id
  ): IO[SettingsService.Result] = for {
    profile <- profileStore.profile(profileId).map(_.get)
    resolved <- resolveSettings(quickSettings, payload, profile)
  } yield SettingsService.Result(id = resolved.settings.id, audience = resolved.audience, profile = profile)

  private def resolveSettings(
      quickSettings: Option[QuickSettings],
      payload: CommandPayload,
      profile: Profile
  ): IO[ResolvedSettings] =
    for {
      settingsOpt <- settingsStore.settings(settingsId(payload, profile.id))
      token <- encryption.decrypt(profile.token.encrypted)
      resolved <- settingsOpt match {
        case Some(settings) =>
          for {
            audience <- existingCustomAudience_!(settings.audienceId, token)
          } yield ResolvedSettings(audience = audience, settings = settings)
        case None => createSettings(quickSettings, payload, profile, token)
      }
    } yield resolved

  private def createSettings(
      quickSettings: Option[QuickSettings],
      payload: CommandPayload,
      profile: Profile,
      token: FacebookToken
  ): IO[ResolvedSettings] = {
    val audienceIdOpt = profile.audience.map(_.id).orElse(quickSettings.flatMap(_.audienceId))
    for {
      audience <- audienceIdOpt match {
        case Some(audienceId) => existingCustomAudience_!(audienceId, token)
        case None             => createCustomAudience(profile.adAccount.id, quickSettings, payload, token)
      }
      settings <- settingsStore.upsert(settingsId(payload, profile.id), audience.id)
    } yield ResolvedSettings(audience = audience, settings = settings)
  }

  private def createCustomAudience(
      adAccountId: AdAccount.Id,
      quickSettings: Option[QuickSettings],
      payload: CommandPayload,
      token: FacebookToken
  ): IO[AudienceResponse] =
    for {
      _ <- IO(
        logger.info(
          s"no custom audience specified in profile or quick settings. creating new custom in ad account ${adAccountId.value.show}"
        )
      )
      audience <- fb.createCustomAudience(token, adAccountId, newAudienceName(quickSettings, payload), none)
    } yield audience

  private def existingCustomAudience_!(id: Audience.Id, token: FacebookToken): IO[AudienceResponse] =
    OptionT(fb.customAudiences(token, NonEmptyList.one(id)).map(_.headOption))
      .getOrRaise(new RuntimeException(s"could not find audience with id ${id.show}"))
}

object SettingsService {
  trait ReadOps[F[_]] {}
  trait WriteOps[F[_]] {
    def getOrCreate(
        quickSettings: Option[QuickSettings],
        payload: CommandPayload,
        profileId: Profile.Id
    ): F[Result]
  }

  trait Ops[F[_]] extends ReadOps[F] with WriteOps[F]

  final case class Result(id: Settings.Id, audience: AudienceResponse, profile: Profile)

  private final case class ResolvedSettings(audience: AudienceResponse, settings: Settings)

  private def newAudienceName(quickSettings: Option[QuickSettings], payload: CommandPayload): Audience.Name = {
    quickSettings
      .flatMap(_.audienceName)
      .getOrElse(payload match {
        case Job.ProcessCommand(subscriptionId, _) => Audience.Name(s"Subscription ${subscriptionId.show} Audience")
      })
  }

  private def settingsId(payload: CommandPayload, profileId: Profile.Id): Settings.Id = payload match {
    case Job.ProcessCommand(subscriptionId, _) => Settings.Id(profileId, subscriptionId)
  }
}
