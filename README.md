# facebook-connector

A connector allowing users to send audience data to Facebook Custom Audiences inside their platform advertising account.

| Architecture                         | Operations                       | Infra/Terraform    |
|--------------------------------------|----------------------------------|--------------------|
| [architecture.md](./architecture.md) | [operations.md](./operations.md) | [infra/](./infra/) |

**Table of Contents**

- [facebook-connector](#facebook-connector)
    - [Project Structure](#project-structure)
    - [Workstation Setup](#workstation-setup)
    - [Build and Automated Tests](#build-and-automated-tests)
    - [Running Locally](#running-locally)
        - [API](#api)
        - [Worker](#worker)
    - [Manual Tests](#manual-tests)
    - [Relevant Facebook Resources](#relevant-facebook-resources)

## Project Structure

- [backend](./backend/)
- [frontend](./frontend/)

## Workstation Setup

These setup instructions complement the
general [Dev Workstation Setup](https://jobs.narrative.io/process/dev-workstation-setup) instructions.

```bash
asdf install
```

## Build and Automated Tests

```bash
sbt test
```

## Running Locally

### API

- Run against dev DB:

```bash
DISABLE_JSON_LOGS=true \
TOKEN_KMS_KEY_ID=11bdad18-80b4-4107-a854-a643eee7c7cb \
STAGE=dev \
./run-api.sh
```

- Run against local DB:

```bash
DISABLE_JSON_LOGS=true \
TOKEN_KMS_KEY_ID=11bdad18-80b4-4107-a854-a643eee7c7cb \
API_PORT=9998 \
DB_USER=root \
DB_PASSWORD=narrative
DB_URL=jdbc:postgresql://localhost:5432/facebookconnector ./run-api.sh
```

See [the API config](./api/src/main/scala/io/narrative/connectors/facebook/Config.scala) for more configuration options.

### Worker

- Run against dev DB:

```bash
DISABLE_JSON_LOGS=true \
TOKEN_KMS_KEY_ID=11bdad18-80b4-4107-a854-a643eee7c7cb \
STAGE=dev \
./run-worker.sh
```

- Run against local DB:

```bash
DISABLE_JSON_LOGS=true \
TOKEN_KMS_KEY_ID=11bdad18-80b4-4107-a854-a643eee7c7cb \
DB_USER=root \
DB_PASSWORD=narrative
DB_URL=jdbc:postgresql://localhost:5432/facebookconnector ./run-worker.sh
```

See [the Worker config](./worker/src/main/scala/io/narrative/connectors/facebook/Config.scala) for more configuration
options.

## Relevant Facebook Resources

- [Facebook Marketing API](https://developers.facebook.com/docs/marketing-apis/)
- [Facebook API Explorer](https://developers.facebook.com/tools/explorer/)
- [Facebook Ad Account](https://www.facebook.com/business/help/407323696966570?id=649869995454285)
- [Facebook Access Tokens](https://developers.facebook.com/docs/facebook-login/guides/access-tokens)
- [Facebook Custom Audiences](https://developers.facebook.com/docs/marketing-api/audiences/guides/custom-audiences/)
