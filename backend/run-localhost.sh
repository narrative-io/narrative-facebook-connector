set -euo pipefail

echo $(env)

echo "sbt $@ api/runMain io.narrative.connectors.facebook.Server"
sbt $@ "api/runMain io.narrative.connectors.facebook.Server"
