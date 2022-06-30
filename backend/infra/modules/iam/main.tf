resource "aws_iam_role" "_" {
  name = "${var.name_prefix}-${var.stage}"
  assume_role_policy = data.aws_iam_policy_document.assume_role.json
}

resource "aws_iam_role_policy" "kms" {
  name = "${var.name_prefix}-kms-${var.stage}"
  role = aws_iam_role._.id
  policy = data.aws_iam_policy_document.kms.json
}

resource "aws_iam_role_policy" "ssm" {
  name = "${var.name_prefix}-ssm-${var.stage}"
  role = aws_iam_role._.id
  policy = data.aws_iam_policy_document.ssm.json
}


data "aws_iam_policy_document" "assume_role" {
  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type = "Service"

      identifiers = [
        "ecs-tasks.amazonaws.com"
      ]
    }
  }
}

resource "aws_iam_role_policy_attachment" "base" {
  policy_arn = data.aws_iam_policy.AmazonECSTaskExecutionRolePolicy.arn
  role = aws_iam_role._.name
}

# https://docs.aws.amazon.com/AmazonECS/latest/userguide/task_execution_IAM_role.html
data "aws_iam_policy" "AmazonECSTaskExecutionRolePolicy" {
  arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

data "aws_kms_alias" "ssm" {
  name = var.ssm_key_alias
}
