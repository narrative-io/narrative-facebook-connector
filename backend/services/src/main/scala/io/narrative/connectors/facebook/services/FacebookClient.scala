package io.narrative.connectors.facebook.services

import cats.data.NonEmptyList
import cats.effect.{Blocker, ContextShift, IO, Timer}
import cats.instances.list._
import cats.syntax.show._
import cats.syntax.traverse._
import com.facebook.ads.{sdk => fb}
import com.google.gson.{JsonArray, JsonObject, JsonPrimitive}
import com.typesafe.scalalogging.LazyLogging
import io.narrative.connectors.facebook.domain.{AdAccount, Audience, Business, FacebookUser}
import io.narrative.connectors.facebook.services.TokenMeta.Scope
import retry.RetryPolicies._
import retry._

import java.time.Instant
import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.util.control.NoStackTrace

/** A Facebook API wrapper. */
class FacebookClient(appId: String, appSecret: String, blocker: Blocker)(implicit
    cs: ContextShift[IO],
    timer: Timer[IO]
) extends FacebookClient.Ops[IO]
    with LazyLogging {
  import FacebookClient._

  private val retryPolicy = limitRetries[IO](5).join(exponentialBackoff[IO](10.milliseconds))

  /** Ad accounts by IDs for the user associated with the given token. */
  override def adAccount(accessToken: FacebookToken, id: AdAccount.Id): IO[Option[AdAccountResponse]] =
    adAccounts(accessToken, NonEmptyList.one(id)).map(_.headOption)

  /** @inheritdoc */
  override def adAccounts(accessToken: FacebookToken): IO[List[AdAccountResponse]] =
    for {
      fbAdAccounts <- runIO {
        new fb.APIRequest(mkContext(accessToken), "me", "/adaccounts", "GET", fb.AdAccount.getParser)
          .setParam("limit", 100) // page size
          .requestField("id")
          .requestField("name")
          .requestField("business")
          .requestField("user_tos_accepted")
          .execute()
          .asInstanceOf[fb.APINodeList[fb.AdAccount]]
          .withAutoPaginationIterator(true)
          // NB: iterates through all pages of results, okay for now since we don't except customers to have thousands
          // of ad accounts
          .iterator()
          .asScala
          .toList
      }
    } yield fbAdAccounts
      .filter(adAccount => !ExcludedAdAccounts.contains(adAccount.getId))
      .map(mkAdAccount)

  /** @inheritdoc */
  override def adAccounts(
      accessToken: FacebookToken,
      adAccountIds: NonEmptyList[AdAccount.Id]
  ): IO[List[AdAccountResponse]] =
    runIO(
      fb.AdAccount.fetchByIds(
        adAccountIds.map(_.value).toList.asJava,
        List("account_id", "name", "business", "user_tos_accepted").asJava,
        mkContext(accessToken)
      )
    ).map(_.asScala.toList.map(mkAdAccount))

  /** @inheritdoc */
  override def customAudiences(
      accessToken: FacebookToken,
      audienceIds: NonEmptyList[Audience.Id]
  ): IO[List[AudienceResponse]] =
    for {
      fbCustomAudiences <- fbCustomAudiences(accessToken, audienceIds)
      adAccountIdsOpt = NonEmptyList.fromList(
        fbCustomAudiences.map(_.getFieldAccountId).map(AdAccount.Id.fromUnprefixed_!).distinct
      )
      adAccounts <- adAccountIdsOpt match {
        case Some(adAccountIds) => adAccounts(accessToken, adAccountIds)
        case None               => IO.pure(List.empty)
      }
    } yield mkCustomAudiences(fbCustomAudiences, adAccounts)

  /** @inheritdoc */
  override def tokenMeta(accessToken: FacebookToken): IO[TokenMeta] =
    // Technically we should be able to make the token props and user requests as part of a batch, but Facebook's
    // Java SDK doesn't support using a different access token per request in the batch even though the API does.
    for {
      tokenProps <- tokenProps(accessToken)
      (isValid, scopes, issuedAt) = tokenProps
      meta <-
        if (isValid)
          for {
            user <- facebookUser(accessToken)
          } yield TokenMeta.Valid(
            issuedAt = issuedAt.getOrElse(Instant.now()),
            scopes = scopes,
            user = user
          )
        else
          IO.pure(TokenMeta.Invalid)
    } yield meta

  override def addToAudience(
      accessToken: FacebookToken,
      audienceId: Audience.Id,
      members: List[FacebookAudienceMember]
  ): IO[Unit] = {
    // The below is gross, but is mimicking the official example:
    // https://github.com/facebook/facebook-java-business-sdk/blob/71ff19da9131cadcddecb55d5f194d0b7f12b480/examples/src/main/java/com/facebook/ads/sdk/samples/CustomAudienceExample.java
    val schema = new JsonArray()
    // NB: only support MAID deliveries in the MVP
    schema.add(new JsonPrimitive("MADID"))

    def addBatch(audience: fb.CustomAudience, batch: List[FacebookAudienceMember]): IO[Unit] = {
      logger.info(s"posting ${batch.size} members to facebook audience ${audienceId.value}")
      val data = new JsonArray
      batch.foreach { member =>
        val row = new JsonArray()
        row.add(new JsonPrimitive(member.maid.getOrElse("")))
        data.add(row)
      }
      val payload = new JsonObject()
      payload.add("schema", schema)
      payload.add("data", data)
      val createUser = audience.createUser()
      createUser.setPayload(payload)
      runIO(createUser.execute()).void
    }

    for {
      audience <- runIO(fb.CustomAudience.fetchById(audienceId.value, mkContext(accessToken)))
      _ <- members.grouped(AddToAudienceMaxBatchSize).toList.traverse(addBatch(audience, _))
    } yield ()
  }

  // todo(mbabic) customer retention?
  /** @inheritdoc */
  override def createCustomAudience(
      accessToken: FacebookToken,
      adAccountId: AdAccount.Id,
      name: Audience.Name,
      description: Option[String]
  ): IO[AudienceResponse] =
    for {
      customAudience <- runIO(
        new fb.AdAccount(adAccountId.value, mkContext(accessToken))
          .createCustomAudience()
          .setName(name.value)
          .setDescription(description.getOrElse("").take(100))
          .setSubtype(fb.CustomAudience.EnumSubtype.VALUE_CUSTOM)
          .setCustomerFileSource(
            fb.CustomAudience.EnumCustomerFileSource.VALUE_PARTNER_PROVIDED_ONLY
          )
          .execute()
      )
      adAccounts <- adAccounts(accessToken, NonEmptyList.one(adAccountId))
    } yield mkCustomAudiences(List(customAudience), adAccounts) match {
      case value :: _ => value
      case Nil =>
        throw new RuntimeException(
          s"unexpected error created custom audience: failed to construct response, but custom audience ${customAudience.getFieldName} (${customAudience.getId}) created in ad account ${adAccountId.show}"
        )
    }

  /** @inheritdoc */
  override def exchangeForLongLivedToken(accessToken: FacebookToken): IO[FacebookToken] =
    for {
      accessTokenJson <- runIO(
        new fb.APIRequest(mkContext(accessToken), "oauth", "/access_token", "GET")
          .setParam("grant_type", "fb_exchange_token")
          .setParam("client_id", appId)
          .setParam("client_secret", appSecret)
          .setParam("fb_exchange_token", accessToken.value)
          .execute()
          .getRawResponseAsJsonObject()
      )
    } yield FacebookToken(accessTokenJson.get("access_token").getAsString)

  /** @inheritdoc */
  override def revoke(accessToken: FacebookToken): IO[Unit] =
    runIO(new fb.APIRequest(mkContext(accessToken), "me", "/permissions", "DELETE").execute()).void

  private def fbCustomAudiences(
      accessToken: FacebookToken,
      audienceIds: NonEmptyList[Audience.Id]
  ): IO[List[fb.CustomAudience]] =
    runIO(
      fb.CustomAudience.fetchByIds(
        audienceIds.toList.map(_.value).asJava,
        List("id", "account_id", "name", "description", "time_created").asJava,
        mkContext(accessToken)
      )
    ).map(_.asScala.toList)

  private def tokenProps(accessToken: FacebookToken): IO[(Boolean, List[Scope], Option[Instant])] = {
    // Calls to the debug_token require that we use an app access token instead of the user's access token.
    // An app access token can either be fetched explicitly or created by concatenating the appId and the appSecret.
    // We choose the latter for simplicity since we have everything we need in scope.
    val appTokenCtx = mkContext(FacebookToken(s"${appId}|${appSecret}"))
    val debugTokenReq =
      new fb.APIRequest(appTokenCtx, "debug_token", "/", "GET")
        .setParam("input_token", accessToken.value)
        .requestField("is_valid")
        .requestField("issued_at")
        .requestField("scopes")
    for {
      debugTokenResp <- runIO(debugTokenReq.execute())
      debugTokenJson = debugTokenResp.getRawResponseAsJsonObject.get("data").getAsJsonObject
      isValid = debugTokenJson.get("is_valid").getAsBoolean
      scopes = debugTokenJson.getAsJsonArray("scopes").asScala.toList.map(_.getAsString).map(Scope.parse)
      issuedAt = Option(debugTokenJson.get("issued_at")).map(_.getAsLong * 1000L).map(Instant.ofEpochMilli)
    } yield (isValid, scopes, issuedAt)
  }

  private def facebookUser(accessToken: FacebookToken): IO[FacebookUserResponse] = {
    val req = new fb.APIRequest(mkContext(accessToken), "me", "/", "GET", fb.User.getParser)
      .requestField("id")
      .requestField("name")
    for {
      userResp <- runIO(req.execute())
      fbUser = userResp.asInstanceOf[fb.APINodeList[fb.User]].get(0)
    } yield FacebookUserResponse(
      id = FacebookUser.Id(fbUser.getId),
      name = FacebookUser.Name(fbUser.getFieldName)
    )
  }

  private def runIO[A](apiCall: => A): IO[A] =
    retryingOnSomeErrors(retryPolicy, shouldRetry, logError) {
      blocker.blockOn(
        IO(apiCall)
          .timeout(HttpTimeout)
          .handleErrorWith {
            // https://developers.facebook.com/docs/graph-api/using-graph-api/error-handling/
            case e: fb.APIException.FailedRequestException if getErrorCode(e).contains(190) =>
              IO.raiseError(InvalidAccessToken)
          }
      )
    }
}

object FacebookClient extends LazyLogging {

  /** The maximum number of records that can be added to a custom audience in a single API call. */
  val AddToAudienceMaxBatchSize: Int = 10000

  // https://developers.facebook.com/docs/graph-api/using-graph-api/error-handling/
  private val RetryableErrorCodes: Set[Int] =
    Set(
      1, // API Unknown: possibly temporary, possibly user error, we'll retry either way
      2, // API Service: temporary issue due to downtime
      4, // API Too Many Calls
      17, // API User Too Many Calls
      341, // Application limit reach: temporary issue due to downtime or throttling
      368 // Temporarily blocked for policies violations
    )

  private val HttpTimeout = 20.seconds
  // Ad accounts that we don't want to make selectable for various reasons.
  // - 1212393198887712: https://app.shortcut.io/narrativeio/story/1872
  private val ExcludedAdAccounts = Set("1212393198887712")

  trait ReadOps[F[_]] {

    /** Ad accounts by IDs for the user associated with the given token. */
    def adAccount(accessToken: FacebookToken, id: AdAccount.Id): F[Option[AdAccountResponse]]

    /** Ad accounts for the user associated with the given token. */
    def adAccounts(accessToken: FacebookToken): F[List[AdAccountResponse]]

    /** Ad accounts by IDs for the user associated with the given token. */
    def adAccounts(accessToken: FacebookToken, adAccountIds: NonEmptyList[AdAccount.Id]): F[List[AdAccountResponse]]

    /** Custom audiences by IDs for the user associated with the given token. */
    def customAudiences(accessToken: FacebookToken, audienceIds: NonEmptyList[Audience.Id]): F[List[AudienceResponse]]

    /** Relevant metadata for the given token. Used to determine e.g. if the token is still valid. */
    def tokenMeta(accessToken: FacebookToken): F[TokenMeta]
  }

  trait WriteOps[F[_]] {

    /** Add the given members to the given audience. */
    def addToAudience(accessToken: FacebookToken, audienceId: Audience.Id, batch: List[FacebookAudienceMember]): F[Unit]

    /** Create a custom audience associated in the given ad account. */
    def createCustomAudience(
        accessToken: FacebookToken,
        adAccountId: AdAccount.Id,
        name: Audience.Name,
        description: Option[String]
    ): F[AudienceResponse]

    /** Exchange the given token for a long-lived token appopriate for use in backend server-to-server processes. */
    def exchangeForLongLivedToken(accessToken: FacebookToken): F[FacebookToken]

    /** Delete the permissions associated with a token, removing the application's ability to use the token. */
    def revoke(accessToken: FacebookToken): F[Unit]
  }

  trait Ops[F[_]] extends ReadOps[F] with WriteOps[F]

  sealed trait Error extends NoStackTrace
  case object InvalidAccessToken extends Error

  private def mkCustomAudiences(fbCustomAudiences: List[fb.CustomAudience], adAccounts: List[AdAccountResponse]) =
    for {
      fbCustomAudience <- fbCustomAudiences
      adAccount <- adAccounts
      if adAccount.id.withoutPrefix == fbCustomAudience.getFieldAccountId
    } yield AudienceResponse(
      id = Audience.Id(fbCustomAudience.getFieldId),
      adAccount = adAccount,
      createdAt = Instant.ofEpochMilli(fbCustomAudience.getFieldTimeCreated * 1000L),
      description = Option(fbCustomAudience.getFieldDescription).filter(_.nonEmpty),
      name = Audience.Name(fbCustomAudience.getFieldName)
    )

  private def mkAdAccount(fbAdAccount: fb.AdAccount): AdAccountResponse =
    AdAccountResponse(
      id = AdAccount.Id.fromUnprefixed_!(fbAdAccount.getId),
      name = AdAccount.Name(fbAdAccount.getFieldName),
      business = mkBusiness(fbAdAccount),
      supportsCustomAudiences = mkBusiness(fbAdAccount).isDefined && hasUserAcceptedCustomAudienceTos(fbAdAccount),
      userAcceptedCustomAudienceTos = hasUserAcceptedCustomAudienceTos(fbAdAccount)
    )

  private def mkBusiness(fbAdAccount: fb.AdAccount): Option[BusinessResponse] =
    Option(fbAdAccount.getFieldBusiness)
      .map(business => BusinessResponse(Business.Id(business.getId), Business.Name(business.getFieldName)))

  // https://developers.facebook.com/docs/marketing-api/audiences/reference/custom-audience-terms-of-service
  private def hasUserAcceptedCustomAudienceTos(fbAdAccount: fb.AdAccount): Boolean =
    Option(fbAdAccount.getFieldUserTosAccepted)
      .flatMap(_.asScala.get("custom_audience_tos").map(_ == 1L))
      .getOrElse(false)

  private def mkContext(accessToken: FacebookToken): fb.APIContext = new fb.APIContext(accessToken.value)

  private def shouldRetry(t: Throwable): Boolean = t match {
    case failedRequest: fb.APIException.FailedRequestException =>
      getErrorCode(failedRequest).exists(RetryableErrorCodes.contains)
    case _ => false
  }

  private def logError(t: Throwable, retryDetails: RetryDetails): IO[Unit] =
    t match {
      case failedRequest: fb.APIException.FailedRequestException =>
        IO(
          logger.warn(
            s"Caught exception performing Facebook API request (${retryDetails}): ${failedRequest.getRawResponseAsJsonObject}"
          )
        )
      case _ =>
        IO(
          logger.warn(
            s"Caught exception performing Facebook API request (${retryDetails}): {}",
            t
          )
        )
    }

  private def getErrorCode(failedRequest: fb.APIException.FailedRequestException): Option[Int] =
    for {
      errorJson <- Option(failedRequest.getRawResponseAsJsonObject.get("error"))
      codeJson <- Option(errorJson.getAsJsonObject.get("code"))
    } yield codeJson.getAsInt
}
