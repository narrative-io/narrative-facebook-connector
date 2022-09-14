resource "aws_lambda_permission" "_" {
  function_name = "arn:aws:lambda:us-east-1:704349335716:function:notification-publisher-publish-sns-slack-${var.stage}"
  action = "lambda:InvokeFunction"
  principal = "logs.amazonaws.com"
  source_arn = "${var.log_group_arn}:*"
}

resource "aws_cloudwatch_log_subscription_filter" "_" {
  name            = var.filter_name
  log_group_name  = var.log_group_name
  filter_pattern  = "{$.level = \"ERROR\"}"
  destination_arn = "arn:aws:lambda:us-east-1:704349335716:function:notification-publisher-publish-sns-slack-${var.stage}"
  depends_on = [aws_lambda_permission._]
}
