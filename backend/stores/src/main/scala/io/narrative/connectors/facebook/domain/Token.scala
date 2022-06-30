package io.narrative.connectors.facebook.domain

import cats.Show
import cats.kernel.Eq
import doobie.Meta

import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Base64

/** A persisted, encrypted Facebook API token. */
case class Token(encrypted: Token.Encrypted, issuedAt: Instant, user: FacebookUser)
object Token {
  final case class Encrypted(value: Array[Byte]) extends AnyVal
  object Encrypted {
    implicit val eq: Eq[Encrypted] = Eq.fromUniversalEquals
    implicit val meta: Meta[Encrypted] = Meta[Array[Byte]].imap(Encrypted.apply)(_.value)
    implicit val show: Show[Encrypted] =
      Show.show(encrypted => new String(Base64.getEncoder.encode(encrypted.value), StandardCharsets.US_ASCII))
  }
}
