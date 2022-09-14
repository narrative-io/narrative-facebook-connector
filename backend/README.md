# Facebook Connector

A connector allowing users to send audience data to Facebook Custom Audiences inside their platform advertising account.

See the [runbook](./runbook.md) for more details including data and customer flows, operational information, etc.

## Development

### Build and Test

```bash
sbt test
```

### Running Locally

For more context on the components that make up the connector see [the runbook](./runbook.md).

#### API

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

#### Worker

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

See [the Worker config](./worker/src/main/scala/io/narrative/connectors/facebook/Config.scala) for more configuration options.