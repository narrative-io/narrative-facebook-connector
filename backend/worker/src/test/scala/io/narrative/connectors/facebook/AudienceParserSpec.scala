package io.narrative.connectors.facebook

import cats.syntax.option._
import io.circe.Json
import io.circe.literal._
import io.narrative.connectors.facebook.services.FacebookAudienceMember
import org.scalatest.{EitherValues, OptionValues}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class AudienceParserSpec extends AnyFunSuite with Matchers with OptionValues with EitherValues {

  test("parse birth info") {
    val birthDate = json"""
    {
      "data": {
        "birthdate": "1980-03-10T00:00:00Z"
      }
    }
    """
    val birthYear = json"""
    {
      "data": {
        "birth_year": 1999
      }
    }
    """
    val invalid = json"""
    {
      "data": {
        "foobar": 1999
      }
    }
    """

    AudienceParser.parseLegacy(birthDate) shouldEqual FacebookAudienceMember(
      // sha256("10")
      birthDay = "4a44dc15364204a80fe80e9039455cc1608281820fe2b24f1e5233ade6af1dd5".some,
      // sha256("03")
      birthMonth = "0b8efa5a3bf104413a725c6ff0459a6be12b1fd33314cbb138745baf39504ae5".some,
      // sha256("1980")
      birthYear = "051c2e380d07844ffaca43743957f8c0efe2bdf74c6c1e6a9dcccb8d1a3c596b".some
    )
    AudienceParser.parseLegacy(birthYear) shouldEqual FacebookAudienceMember(
      // sha256("1999")
      birthYear = "ce8457d59078a699acb70416f88155a96a906b7b7aad43708402e3a3bcc8a4b4".some
    )
    AudienceParser.parseLegacy(invalid) shouldEqual FacebookAudienceMember(
      birthDay = none,
      birthMonth = none,
      birthYear = none
    )
  }

  test("parse country") {
    val valid = json"""
    {
      "data": {
        "iso_3166_1_country": "CA"
      }
    }
    """
    val invalid = json"""
    {
      "data": {
        "foo": "bar"
      }
    }
    """

    AudienceParser.parseLegacy(valid) shouldEqual FacebookAudienceMember(
      // sha256("ca")
      country = "6959097001d10501ac7d54c0bdb8db61420f658f2922cc26e46d536119a31126".some
    )
    AudienceParser.parseLegacy(invalid).country shouldEqual none
  }

  test("parse gender") {
    val female = json"""
    {
      "data": {
        "hl7_gender": {
          "gender": "female"
        }
      }
    }
    """
    val male = json"""
    {
      "data": {
        "hl7_gender": {
          "gender": "male"
        }
      }
    }
    """
    val invalid = json"""
    {
      "data": {
        "foo": "bar"
      }
    }
    """

    AudienceParser.parseLegacy(female) shouldEqual FacebookAudienceMember(
      // sha256("f")
      gender = "252f10c83610ebca1a059c0bae8255eba2f95be4d1d7bcfa89d7248a82d9f111".some
    )
    AudienceParser.parseLegacy(male) shouldEqual FacebookAudienceMember(
      // sha256("m")
      gender = "62c66a7a5dd70c3146618063c344e531e6d4b59e379808443ce962b3abd63c5a".some
    )
    AudienceParser.parseLegacy(invalid).gender shouldEqual none
  }

  test("parse hem from unique_id") {
    val valid = json"""
    {
      "data": {
        "unique_id": {
          "type": "sha256_email",
          "value": "d64ac45af268c53ea74ad3ef3dbc7102cd0aa9b63cb59456122be981d3bf2ade"
        }
      }
    }
    """
    val invalid = json"""
    {
      "data": {
        "unique_id": {
          "type": "sha1_email",
          "value": "32c281951528fda472cc53ea2c5c81b9d50f184e"
        }
      }
    }
    """

    AudienceParser.parseLegacy(valid) shouldEqual FacebookAudienceMember(
      email = "d64ac45af268c53ea74ad3ef3dbc7102cd0aa9b63cb59456122be981d3bf2ade".some
    )
    AudienceParser.parseLegacy(invalid).email shouldEqual none
  }

  test("parse hem from hashed_email") {
    val valid = json"""
    {
      "data": {
        "hashed_email": {
          "type": "sha256_email",
          "value": "d64ac45af268c53ea74ad3ef3dbc7102cd0aa9b63cb59456122be981d3bf2ade"
        }
      }
    }
    """
    val invalid = json"""
    {
      "data": {
        "hashed_email": {
          "type": "sha1_email",
          "value": "32c281951528fda472cc53ea2c5c81b9d50f184e"
        }
      }
    }
    """

    AudienceParser.parseLegacy(valid) shouldEqual FacebookAudienceMember(
      email = "d64ac45af268c53ea74ad3ef3dbc7102cd0aa9b63cb59456122be981d3bf2ade".some
    )
    AudienceParser.parseLegacy(invalid).email shouldEqual none
  }

  test("parse hem from sha256") {
    val valid = json"""
    {
      "data": {
        "sha256_hashed_email": {
          "value": "d64ac45af268c53ea74ad3ef3dbc7102cd0aa9b63cb59456122be981d3bf2ade"
        }
      }
    }
    """
    AudienceParser.parseLegacy(valid) shouldEqual FacebookAudienceMember(
      email = "d64ac45af268c53ea74ad3ef3dbc7102cd0aa9b63cb59456122be981d3bf2ade".some
    )
  }

  test("parse hem with multiple valid sources") {
    val validUniqueIdWithHem = json"""
    {
      "data": {
        "hashed_email": {
          "type": "sha256_email",
          "value": "0b9383907749fcb5121c25f9af1710e192cc521c85f49eeb3742a44bd6af66ea"
        },
        "unique_id": {
          "type": "sha256_email",
          "value": "d64ac45af268c53ea74ad3ef3dbc7102cd0aa9b63cb59456122be981d3bf2ade"
        }
      }
    }
    """
    val invalidUniqueIdWithHem = json"""
    {
      "data": {
        "hashed_email": {
          "type": "sha256_email",
          "value": "0b9383907749fcb5121c25f9af1710e192cc521c85f49eeb3742a44bd6af66ea"
        },
        "unique_id": {
          "type": "sha1_email",
          "value": "32c281951528fda472cc53ea2c5c81b9d50f184e"
        }
      }
    }
    """
    val validHemWithSha256 = json"""
    {
      "data": {
        "hashed_email": {
          "type": "sha256_email",
          "value": "0b9383907749fcb5121c25f9af1710e192cc521c85f49eeb3742a44bd6af66ea"
        },
        "sha256_hashed_email": {
          "value": "850a1ec9bdbb01c9650344d96e0403fe27b4ce75efbad620b14b47692340bb78"
        }
      }
    }
    """
    val invalidHemWithSha256 = json"""
    {
      "data": {
        "hashed_email": {
          "type": "sha1_email",
          "value": "32c281951528fda472cc53ea2c5c81b9d50f184e"
        },
        "sha256_hashed_email": {
          "value": "850a1ec9bdbb01c9650344d96e0403fe27b4ce75efbad620b14b47692340bb78"
        }
      }
    }
    """

    AudienceParser.parseLegacy(validUniqueIdWithHem) shouldEqual FacebookAudienceMember(
      email = "d64ac45af268c53ea74ad3ef3dbc7102cd0aa9b63cb59456122be981d3bf2ade".some
    )
    AudienceParser.parseLegacy(invalidUniqueIdWithHem) shouldEqual FacebookAudienceMember(
      email = "0b9383907749fcb5121c25f9af1710e192cc521c85f49eeb3742a44bd6af66ea".some
    )
    AudienceParser.parseLegacy(validHemWithSha256) shouldEqual FacebookAudienceMember(
      email = "0b9383907749fcb5121c25f9af1710e192cc521c85f49eeb3742a44bd6af66ea".some
    )
    AudienceParser.parseLegacy(invalidHemWithSha256) shouldEqual FacebookAudienceMember(
      email = "850a1ec9bdbb01c9650344d96e0403fe27b4ce75efbad620b14b47692340bb78".some
    )
  }

  test("parse maid from adid") {
    val valid = json"""
    {
      "data": {
        "android_advertising_id": {
          "value": "98c4a93c-0101-4898-90b4-9a15c97150cd"
        }
      }
    }
    """
    val invalid = json"""
    {
      "data": {
        "foo": "bar"
      }
    }
    """
    AudienceParser.parseLegacy(valid) shouldEqual FacebookAudienceMember(
      maid = "98c4a93c-0101-4898-90b4-9a15c97150cd".some
    )
    AudienceParser.parseLegacy(invalid).maid shouldEqual none
  }

  test("parse maid from idfa") {
    val valid = json"""
    {
      "data": {
        "apple_idfa": {
          "value": "98c4a93c-0101-4898-90b4-9a15c97150cd"
        }
      }
    }
    """
    val invalid = json"""
    {
      "data": {
        "foo": "bar"
      }
    }
    """
    AudienceParser.parseLegacy(valid) shouldEqual FacebookAudienceMember(
      maid = "98c4a93c-0101-4898-90b4-9a15c97150cd".some
    )
    AudienceParser.parseLegacy(invalid).maid shouldEqual none
  }

  test("parse maid from maid") {
    val valid = json"""
    {
      "data": {
        "mobile_id_unique_identifier": {
          "value": "98c4a93c-0101-4898-90b4-9a15c97150cd"
        }
      }
    }
    """
    val invalid = json"""
    {
      "data": {
        "foo": "bar"
      }
    }
    """
    AudienceParser.parseLegacy(valid) shouldEqual FacebookAudienceMember(
      maid = "98c4a93c-0101-4898-90b4-9a15c97150cd".some
    )
    AudienceParser.parseLegacy(invalid).maid shouldEqual none
  }

  test("parse maid from unique_id") {
    val adid = json"""
    {
      "data": {
        "unique_id": {
          "type": "adid",
          "value": "966a15b1-6505-4cb7-ba30-3342c5019112"
        }
      }
    }
    """
    val idfa = json"""
    {
      "data": {
        "unique_id": {
          "type": "idfa",
          "value": "4c817c83-61e6-45c1-83d6-17492e04ba97"
        }
      }
    }
    """
    val noIdType = json"""
    {
      "data": {
        "unique_id": {
          "value": "98c4a93c-0101-4898-90b4-9a15c97150cd"
        }
      }
    }
    """
    val invalid = json"""
    {
      "data": {
        "unique_id": {
          "type": "ttd_id",
          "value": "4c817c83-61e6-45c1-83d6-17492e04ba97"
        }
      }
    }
    """

    AudienceParser.parseLegacy(adid) shouldEqual FacebookAudienceMember(
      maid = "966a15b1-6505-4cb7-ba30-3342c5019112".some
    )
    AudienceParser.parseLegacy(idfa) shouldEqual FacebookAudienceMember(
      maid = "4c817c83-61e6-45c1-83d6-17492e04ba97".some
    )
    AudienceParser.parseLegacy(noIdType) shouldEqual FacebookAudienceMember(
      maid = "98c4a93c-0101-4898-90b4-9a15c97150cd".some
    )
    AudienceParser.parseLegacy(invalid).email shouldEqual none
  }

  test("parse maid with multiple valid sources") {
    val validUniqueIdWithIdfa = json"""
    {
      "data": {
        "apple_idfa": {
          "value": "98c4a93c-0101-4898-90b4-9a15c97150cd" 
        },
        "unique_id": {
          "type": "adid",
          "value": "efd1fd36-1827-41a5-8183-b6230a77613f"
        }
      }
    }
    """
    val invalidUniqueIdWithIdfa = json"""
    {
      "data": {
        "apple_idfa": {
          "value": "98c4a93c-0101-4898-90b4-9a15c97150cd" 
        },
        "unique_id": {
          "type": "sha1_email",
          "value": "32c281951528fda472cc53ea2c5c81b9d50f184e"
        }
      }
    }
    """
    val validMaidWithIdfa = json"""
    {
      "data": {
        "apple_idfa": {
          "value": "98c4a93c-0101-4898-90b4-9a15c97150cd" 
        },
        "mobile_id_unique_identifier": {
          "value": "efd1fd36-1827-41a5-8183-b6230a77613f"
        }
      }
    }
    """

    AudienceParser.parseLegacy(validUniqueIdWithIdfa) shouldEqual FacebookAudienceMember(
      maid = "efd1fd36-1827-41a5-8183-b6230a77613f".some
    )
    AudienceParser.parseLegacy(invalidUniqueIdWithIdfa) shouldEqual FacebookAudienceMember(
      maid = "98c4a93c-0101-4898-90b4-9a15c97150cd".some
    )
    AudienceParser.parseLegacy(validMaidWithIdfa) shouldEqual FacebookAudienceMember(
      maid = "efd1fd36-1827-41a5-8183-b6230a77613f".some
    )
  }

  test("parse name") {
    val valid = json"""
    {
      "data": {
        "person_name": {
          "family_name": "Bowers",
          "full_name": "Alice Bowers",
          "given_name": "Alice"
        }
      }
    }
    """
    val firstName = json"""
    {
      "data": {
        "person_name": {
          "full_name": "Alice Bowers",
          "given_name": "Alice"
        }
      }
    }
    """
    val lastName = json"""
    {
      "data": {
        "person_name": {
          "family_name": "Bowers",
          "full_name": "Alice Bowers"
        }
      }
    }
    """
    val fullNameOnly = json"""
    {
      "data": {
        "person_name": {
          "full_name": "Alice Bowers"
        }
      }
    }
    """
    AudienceParser.parseLegacy(valid) shouldEqual FacebookAudienceMember(
      // sha256("alice")
      firstName = "2bd806c97f0e00af1a1fc3328fa763a9269723c8db8fac4f93af71db186d6e90".some,
      // sha256("a")
      firstNameInitial = "ca978112ca1bbdcafac231b39a23dc4da786eff8147c4e72b9807785afee48bb".some,
      // sha256("bowers")
      lastName = "c9561ea9ac17200ef167c850f3268f346f737ad0d3c48ed3996a9ca73689803c".some
    )
    isBackwardCompatible(valid)
    AudienceParser.parseLegacy(firstName) shouldEqual FacebookAudienceMember(
      // sha256("alice")
      firstName = "2bd806c97f0e00af1a1fc3328fa763a9269723c8db8fac4f93af71db186d6e90".some,
      // sha256("a")
      firstNameInitial = "ca978112ca1bbdcafac231b39a23dc4da786eff8147c4e72b9807785afee48bb".some
    )
    isBackwardCompatible(firstName)
    AudienceParser.parseLegacy(lastName) shouldEqual FacebookAudienceMember(
      // sha256("bowers")
      lastName = "c9561ea9ac17200ef167c850f3268f346f737ad0d3c48ed3996a9ca73689803c".some
    )
    isBackwardCompatible(fullNameOnly)
    AudienceParser.parseLegacy(fullNameOnly) shouldEqual FacebookAudienceMember(
      firstName = none,
      firstNameInitial = none,
      lastName = none
    )
  }

  test("parse mix of valid and invalid fields") {
    val valid = json"""
    {
      "data": {
        "birthdate": "1980-03-10T00:00:00Z",
        "iso_3166_1_country": "US",
        "hashed_email": {
          "type": "sha1_email",
          "value": "32c281951528fda472cc53ea2c5c81b9d50f184e"
        },
        "person_name": {
          "family_name": "Bowers",
          "full_name": "Alice Bowers",
          "given_name": "Alice"
        },
        "sha256_hashed_email": {
          "value": "d64ac45af268c53ea74ad3ef3dbc7102cd0aa9b63cb59456122be981d3bf2ade"
        },
        "unique_id": {
          "type": "adid",
          "value": "966a15b1-6505-4cb7-ba30-3342c5019112"
        }
      }
    }
    """

    isBackwardCompatible(valid)
    AudienceParser.parseLegacy(valid) shouldEqual FacebookAudienceMember(
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
      maid = "966a15b1-6505-4cb7-ba30-3342c5019112".some
    )
  }

  def isBackwardCompatible(json: Json) = {
    AudienceParser.parseLegacy(json) shouldEqual AudienceParser.parseDataset(json.hcursor.get[Json]("data").right.value)
  }

}
