set -euo pipefail

sbt $@ "delivery/runMain io.narrative.connectors.facebook.delivery.Main"
