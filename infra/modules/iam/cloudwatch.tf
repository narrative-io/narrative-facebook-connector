data "aws_iam_policy_document" "cloudwatch" {
  statement {
    actions = [
      "cloudwatch:PutMetricData",
    ]

    effect = "Allow"

    # We would ideally restrict metrics to the s3-connector namespace, but I don't think it is possible
    resources = [
      "*"
    ]
  }
}
