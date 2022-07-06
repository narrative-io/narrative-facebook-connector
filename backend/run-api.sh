set -euo pipefail

sbt $@ "api/runMain io.narrative.connectors.facebook.Server"
