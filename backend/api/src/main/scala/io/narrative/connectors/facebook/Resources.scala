package io.narrative.connectors.facebook

import cats.effect.{Blocker, ContextShift, IO, Resource}
import com.amazonaws.auth.{AWSCredentialsProvider, DefaultAWSCredentialsProviderChain}
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagement
import com.typesafe.scalalogging.LazyLogging
import doobie.Transactor
import io.narrative.common.ssm.SSMResources
import org.http4s.blaze.client.BlazeClientBuilder
import org.http4s.client.Client

import java.util.concurrent.Executors
import scala.concurrent.ExecutionContext

final case class Resources(
    awsCredentials: AWSCredentialsProvider,
    blocker: Blocker,
    client: Client[IO],
    serverEC: ExecutionContext,
    ssm: AWSSimpleSystemsManagement,
    xa: Transactor[IO]
)
object Resources extends LazyLogging {
  def apply(config: Config)(implicit contextShift: ContextShift[IO]): Resource[IO, Resources] =
    for {
      blocker <- Blocker[IO]
      awsCredentials = new DefaultAWSCredentialsProviderChain()
      client <- BlazeClientBuilder[IO](blocker.blockingContext).resource
      serverEC <- Resource.fromAutoCloseable(IO(Executors.newFixedThreadPool(64)).map(ExecutionContext.fromExecutor(_)))
      ssm <- SSMResources.ssmClient(
        logger.underlying,
        blocker,
        awsCredentials
      )
    } yield Resources(
      awsCredentials = awsCredentials,
      blocker = blocker,
      client = client,
      serverEC = serverEC,
      ssm = ssm,
      xa = ???
    )
}
