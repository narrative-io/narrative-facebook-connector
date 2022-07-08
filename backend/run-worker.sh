set -euo pipefail

sbt $@ "worker/runMain io.narrative.connectors.facebook.Main"
