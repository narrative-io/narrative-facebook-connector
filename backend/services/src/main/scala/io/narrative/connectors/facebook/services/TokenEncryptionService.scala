package io.narrative.connectors.facebook.services

import cats.effect.{Blocker, ContextShift, IO}
import com.amazonaws.services.kms.AWSKMS
import com.amazonaws.services.kms.model.{DecryptRequest, EncryptRequest}
import io.narrative.connectors.facebook.domain.Token

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

class TokenEncryptionService(blocker: Blocker, keyId: KmsKeyId, kms: AWSKMS)(implicit contextShift: ContextShift[IO])
    extends TokenEncryptionService.Ops[IO] {
  override def decrypt(token: Token.Encrypted): IO[FacebookToken] = {
    val req = new DecryptRequest().withKeyId(keyId.value).withCiphertextBlob(ByteBuffer.wrap(token.value))
    runIO(new String(kms.decrypt(req).getPlaintext.array(), StandardCharsets.UTF_8)).map(FacebookToken.apply)
  }

  override def encrypt(token: FacebookToken): IO[Token.Encrypted] = {
    val req = new EncryptRequest()
      .withKeyId(keyId.value)
      .withPlaintext(ByteBuffer.wrap(token.value.getBytes(StandardCharsets.UTF_8)))
    runIO(kms.encrypt(req).getCiphertextBlob.array()).map(Token.Encrypted.apply)
  }

  private def runIO[A](f: => A): IO[A] = blocker.blockOn(IO(f))
}

object TokenEncryptionService {
  trait Ops[F[_]] {
    def decrypt(token: Token.Encrypted): F[FacebookToken]
    def encrypt(token: FacebookToken): F[Token.Encrypted]
  }
}
