set -euo pipefail

sbt $@ "delivery/runMain io.narrative.connectors.facebook.Worker"
