data "aws_iam_policy_document" "ssm" {
  statement {
    actions = [
      "kms:Decrypt",
    ]

    resources = [
      data.aws_kms_alias.ssm.target_key_arn,
      // ssm-params-prod, used to decrypt Facebook app secret which is shared across environments by necessity
      "arn:aws:kms:us-east-1:704349335716:key/7c5ea61e-638a-420f-8859-ce718b1fceeb"
    ]
  }

  statement {
    actions = [
      "ssm:DescribeParameters",
      "ssm:GetParameters",
      "ssm:GetParameter",
      "ssm:GetParameterHistory",
    ]

    effect = "Allow"

    resources = [
      "arn:aws:ssm:*:704349335716:parameter${var.ssm_db_username}",
      "arn:aws:ssm:*:704349335716:parameter${var.ssm_db_password}",
      "arn:aws:ssm:*:704349335716:parameter${var.ssm_narrative_api_client}",
      "arn:aws:ssm:*:704349335716:parameter${var.ssm_narrative_api_secret}",
      "arn:aws:ssm:*:704349335716:parameter/prod/connectors/facebook/app_id",
      "arn:aws:ssm:*:704349335716:parameter/prod/connectors/facebook/app_secret"
    ]
  }

  # Required for ECS Exec: https://docs.aws.amazon.com/AmazonECS/latest/developerguide/ecs-exec.html#ecs-exec-prerequisites
  statement {
    actions = [
      "ssmmessages:CreateControlChannel",
      "ssmmessages:CreateDataChannel",
      "ssmmessages:OpenControlChannel",
      "ssmmessages:OpenDataChannel"
    ]

    resources = ["*"]
  }
}
