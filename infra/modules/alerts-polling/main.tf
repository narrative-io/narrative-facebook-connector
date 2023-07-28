locals {
  app_name = "facebook-connector"
}

variable "stage" {
  type = string
}

variable "log_group_name" {
  type = string
}

resource "aws_cloudwatch_log_metric_filter" "event_consumer_heartbeat" {
  name           = "${local.app_name}-event-consumer-heartbeat-${var.stage}"
  pattern        = "{$.message = \"*event consumer heartbeat*\"}"
  log_group_name = var.log_group_name

  metric_transformation {
    name      = "event-consumer-heartbeat"
    namespace = "${local.app_name}-${var.stage}"
    value     = "1"
  }
}

resource "aws_cloudwatch_metric_alarm" "foobar" {
  alarm_name          = "${local.app_name}-event-consumer-heartbeat-${var.stage}"
  comparison_operator = "LessThanOrEqualToThreshold"
  evaluation_periods  = 1
  metric_name         = aws_cloudwatch_log_metric_filter.event_consumer_heartbeat.metric_transformation[0].name
  namespace           = aws_cloudwatch_log_metric_filter.event_consumer_heartbeat.metric_transformation[0].namespace
  period              = 3600 // one hour: (60 seconds / minute) * (60 minutes / hour)
  statistic           = "Sum"
  threshold           = 1
  alarm_description   = "Monitors event consumption heartbeats for ${local.app_name}-${var.stage}"
  actions_enabled     = true
  alarm_actions       = [
    "arn:aws:sns:us-east-1:704349335716:app-failure-cwalarms-${var.stage}",
  ]
  ok_actions = [
    "arn:aws:sns:us-east-1:704349335716:app-failure-cwalarms-${var.stage}",
  ]
  insufficient_data_actions = [
    "arn:aws:sns:us-east-1:704349335716:app-failure-cwalarms-${var.stage}",
  ]

  tags = {
    Name        = "${local.app_name}-${var.stage}"
    Stage       = var.stage
    Application = "${local.app_name}"
  }
}
