terraform {
  required_providers {
    datadog = {
      source = "datadog/datadog"
      version = "2.20.0"
    }
  }
}

locals {
  channel = var.stage == "dev" ? "@auto-spam" : "@backend-application-warnings@narrative.io @slack-auto-techops"
  alert = var.stage == "dev" ? "" : "@pagerduty-backend"
  apiname = aws_api_gateway_rest_api._.name
}

resource "datadog_monitor" "latency" {
  name               = "[facebook-connector-api] Errors ${local.apiname}"
  type               = "query alert"
  message = <<EOF
API Errors.
${local.channel}
The Facebook connector API Gateway has thrown errors in the last 30 minutes.
{{#is_alert}}
${local.alert}
{{/is_alert}}

EOF

  query = "sum(last_1h):avg:aws.apigateway.5xxerror{apiname:${local.apiname}}.as_count() > 0"
  tags = ["stage:${var.stage}", "application:facebook-connector-api"]
}