locals {
  app_name = "facebook-connector"
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
  alarm_actions = [
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


resource "datadog_monitor" "queue_size_growing" {
  name = "[${local.app_name}-${var.stage}] Queue size growing"
  type = "metric alert"

  message = <<EOF
The queue size has grown over the past hour.

The queue size out of control contains more information on the possible root cause.  
EOF


  query = "change(min(last_1h),last_5m):avg:facebook_connector.QueueSize{stage:${var.stage}} > 0"

  monitor_thresholds {
    critical = 0
    #critical_recovery = 0
    #warning           = 0
    #warning_recovery  = 0
  }

  require_full_window = true
  evaluation_delay    = 900
  notify_no_data      = true

  renotify_interval = 60
  renotify_statuses = [
    "alert",
    "no data"
  ]

  tags = ["application:${local.app_name}", "stage:${var.stage}"]
}

resource "datadog_monitor" "queue_size_not_changing" {
  name = "[${local.app_name}-${var.stage}] Queue size not changing"
  type = "metric alert"

  message = <<EOF
The queue size has not been changing over the past hour. This is not a concern by itself
but becomes one if the queue size is not empty.
  
EOF


  query = "change(min(last_1h),last_5m):avg:facebook_connector.QueueSize{stage:${var.stage}} == 0"

  monitor_thresholds {
    critical = 0
    #critical_recovery = 0
    #warning           = 0
    #warning_recovery  = 0
  }

  require_full_window = true
  evaluation_delay    = 900
  notify_no_data      = true

  renotify_interval = 60
  renotify_statuses = [
    "alert",
    "no data"
  ]

  tags = ["application:${local.app_name}", "stage:${var.stage}"]
}

resource "datadog_monitor" "queue_size_not_empty" {
  name = "[${local.app_name}-${var.stage}] Queue size non empty for the past hour"
  type = "metric alert"

  message = <<EOF
The queue size has not been empty for the past hour. This is not a concern by itself 
but becomes one if the queue size is either stable or growing.
EOF


  query = "min(last_1h):avg:facebook_connector.QueueSize{stage:${var.stage}} > 0"

  monitor_thresholds {
    critical = 0
    #critical_recovery = 0
    #warning           = 0
    #warning_recovery  = 0
  }

  require_full_window = true
  evaluation_delay    = 900
  notify_no_data      = true

  renotify_interval = 60
  renotify_statuses = [
    "alert",
    "no data"
  ]

  tags = ["application:${local.app_name}", "stage:${var.stage}"]
}

resource "datadog_monitor" "queue_size_out_of_control" {
  name = "[${local.app_name}-${var.stage}] Queue size out of control"
  type = "composite"

  message = <<EOF
The queue size is out of control:
- growing over time, or
- stable, but never being emptied

This could indicate that:
1. The process is stuck / not making progress
2. The process is not able to keep up with the amount of data being transacted

For more details please check the Dashboard:
https://app.datadoghq.com/dashboard/h8x-qvp-nij

@slack-auto-techops
@backend-application-warnings@narrative.io
EOF


  query = "${datadog_monitor.queue_size_growing.id} || (${datadog_monitor.queue_size_not_empty.id} && ${datadog_monitor.queue_size_not_changing.id})"

  monitor_thresholds {
    critical = 0
    #critical_recovery = 0
    #warning           = 0
    #warning_recovery  = 0
  }

  require_full_window = true
  notify_no_data      = true

  renotify_interval = 60
  renotify_statuses = [
    "alert",
    "no data"
  ]

  tags = ["application:${local.app_name}", "stage:${var.stage}"]
}
