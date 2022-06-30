set -euo pipefail

echo "sbt $@ api/runMain io.narrative.connectors.facebook.Server"
sbt $@ "api/runMain io.narrative.connectors.facebook.Server"
