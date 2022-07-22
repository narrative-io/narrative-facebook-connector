package io.narrative.connectors.facebook.services

import cats.syntax.option._
import io.circe.literal._
import io.circe.parser
import org.scalatest.EitherValues
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class FacebookClientSpec extends AnyFunSuite with EitherValues with Matchers {
  test("should generated valid \"add to audience\" payload") {
    val batch = List(
      FacebookAudienceMember(
        // sha256("10")
        birthDay = "4a44dc15364204a80fe80e9039455cc1608281820fe2b24f1e5233ade6af1dd5".some,
        // sha256("03")
        birthMonth = "0b8efa5a3bf104413a725c6ff0459a6be12b1fd33314cbb138745baf39504ae5".some,
        // sha256("1980")
        birthYear = "051c2e380d07844ffaca43743957f8c0efe2bdf74c6c1e6a9dcccb8d1a3c596b".some,
        // sha256("us")
        country = "79adb2a2fce5c6ba215fe5f27f532d4e7edbac4b6a5e09e1ef3a08084a904621".some,
        email = "d64ac45af268c53ea74ad3ef3dbc7102cd0aa9b63cb59456122be981d3bf2ade".some,
        // sha256("alice")
        firstName = "2bd806c97f0e00af1a1fc3328fa763a9269723c8db8fac4f93af71db186d6e90".some,
        // sha256("a")
        firstNameInitial = "ca978112ca1bbdcafac231b39a23dc4da786eff8147c4e72b9807785afee48bb".some,
        // sha256("bowers")
        lastName = "c9561ea9ac17200ef167c850f3268f346f737ad0d3c48ed3996a9ca73689803c".some,
        // sha256("b")
        lastNameInitial = "3e23e8160039594a33894f6564e1b1348bbd7a0088d42c4acb73eeaed59c009d".some,
        maid = "966a15b1-6505-4cb7-ba30-3342c5019112".some
      ),
      FacebookAudienceMember(
        // sha256("m")
        gender = "62c66a7a5dd70c3146618063c344e531e6d4b59e379808443ce962b3abd63c5a".some,
        // sha256("10")
        maid = "966a15b1-6505-4cb7-ba30-3342c5019112".some
      )
    )

    val payload = FacebookClient.mkAddToAudiencePayload(batch)

    parser.parse(payload.toString).right.value shouldEqual
      json"""
      {
        "schema": [
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
        ],
        "data": [
          [
            "4a44dc15364204a80fe80e9039455cc1608281820fe2b24f1e5233ade6af1dd5",
            "0b8efa5a3bf104413a725c6ff0459a6be12b1fd33314cbb138745baf39504ae5",
            "051c2e380d07844ffaca43743957f8c0efe2bdf74c6c1e6a9dcccb8d1a3c596b",
            "79adb2a2fce5c6ba215fe5f27f532d4e7edbac4b6a5e09e1ef3a08084a904621",
            "d64ac45af268c53ea74ad3ef3dbc7102cd0aa9b63cb59456122be981d3bf2ade",
            "2bd806c97f0e00af1a1fc3328fa763a9269723c8db8fac4f93af71db186d6e90",
            "ca978112ca1bbdcafac231b39a23dc4da786eff8147c4e72b9807785afee48bb",
            "",
            "c9561ea9ac17200ef167c850f3268f346f737ad0d3c48ed3996a9ca73689803c",
            "3e23e8160039594a33894f6564e1b1348bbd7a0088d42c4acb73eeaed59c009d",
            "966a15b1-6505-4cb7-ba30-3342c5019112"
          ],
          [
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "62c66a7a5dd70c3146618063c344e531e6d4b59e379808443ce962b3abd63c5a",
            "",
            "",
            "966a15b1-6505-4cb7-ba30-3342c5019112"
          ]
        ]
      }
      """
  }
}
