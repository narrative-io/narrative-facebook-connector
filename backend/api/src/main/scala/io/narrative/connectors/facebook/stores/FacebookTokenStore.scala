package io.narrative.connectors.facebook.stores

import cats.effect.{Blocker, ContextShift, IO}
import cats.syntax.option._
import cats.syntax.show._
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagement
import com.amazonaws.services.simplesystemsmanagement.model.{
  DeleteParameterRequest,
  GetParameterRequest,
  ParameterNotFoundException,
  ParameterType,
  PutParameterRequest
}
import com.typesafe.scalalogging.LazyLogging
import io.narrative.connectors.facebook.domain._
import io.narrative.connectors.facebook.stores.FacebookTokenStore.{StoragePrefix, parameterName}
import io.narrative.microframework.config.Stage

// todo(mbabic) might need profile ID? who knows how this will actually work
class FacebookTokenStore(blocker: Blocker, prefix: StoragePrefix, ssm: AWSSimpleSystemsManagement)(implicit
    contextShift: ContextShift[IO]
) extends FacebookTokenStore.Ops[IO]
    with LazyLogging {
  override def read(companyId: CompanyId): IO[Option[FacebookApiToken]] = {
    val parameter = parameterName(prefix, companyId)
    val req = new GetParameterRequest()
      .withName(parameter)
      .withWithDecryption(true)

    runIO(ssm.getParameter(req)).attempt.map {
      case Right(value) =>
        FacebookApiToken(value.getParameter.getValue).some
      case Left(_: ParameterNotFoundException) =>
        none
      case Left(err) =>
        throw new RuntimeException(
          s"unexpected exception fetching facebook token. companyId=${companyId.show}, parameter=${parameter}",
          err
        )
    }
  }

  override def delete(companyId: CompanyId): IO[Unit] = {
    val parameter = parameterName(prefix, companyId)
    logger.info(s"deleting facebook token. companyId=${companyId.show}, parameter${parameter})")
    val req = new DeleteParameterRequest().withName(parameter)

    runIO(ssm.deleteParameter(req)).attempt.map {
      case Right(_) =>
        ()
      case Left(_: ParameterNotFoundException) =>
        logger.warn(
          s"facebook token deletion failed: no token found. companyId=${companyId.show}, parameter=${parameter}"
        )
      case Left(err) =>
        throw new RuntimeException(
          s"unexpected exception deleting facebook token. companyId=${companyId.show}, parameter=${parameter}",
          err
        )
    }
  }

  override def upsert(companyId: CompanyId, token: FacebookApiToken): IO[Unit] = {
    val parameter = parameterName(prefix, companyId)
    logger.info(s"upserting facebook token. companyId=${companyId.show}, parameter${parameter})")
    val req = new PutParameterRequest()
      .withOverwrite(true)
      .withName(parameter)
      .withType(ParameterType.SecureString)
      .withValue(token.value)

    runIO(ssm.putParameter(req))
  }

  private def runIO[A](f: => A): IO[A] = blocker.blockOn(IO(f))
}

object FacebookTokenStore {
  trait ReadOps[F[_]] {
    def read(companyId: CompanyId): F[Option[FacebookApiToken]]
  }

  trait WriteOps[F[_]] {
    def delete(companyId: CompanyId): IO[Unit]
    def upsert(companyId: CompanyId, token: FacebookApiToken): IO[Unit]
  }

  trait Ops[F[_]] extends ReadOps[F] with WriteOps[F]

  def apply(blocker: Blocker, ssm: AWSSimpleSystemsManagement, stage: Stage)(implicit
      contextShift: ContextShift[IO]
  ): FacebookTokenStore.Ops[IO] = new FacebookTokenStore(blocker, StoragePrefix(stage), ssm)

  final case class StoragePrefix(value: String) extends AnyVal
  object StoragePrefix {
    def apply(stage: Stage): StoragePrefix = StoragePrefix(s"/${stage}/connectors/facebook/tokens/")
  }

  private def parameterName(prefix: StoragePrefix, companyId: CompanyId): String =
    s"${prefix.value}/${companyId}"
}
