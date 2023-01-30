// Key used to encrypt and decrypt Facebook access tokens.
resource "aws_kms_key" "tokens" {
  description = "KMS key used by Facebook Connector in ${var.stage} to encrypt and decrypt Facebook access tokens."
  enable_key_rotation = true
  is_enabled = true
  tags = {
    "Application": "${var.name_prefix}-${var.stage}",
    "Stage": var.stage
  }
}

resource "aws_kms_alias" "tokens" {
  name = "alias/${var.name_prefix}-tokens-${var.stage}"
  target_key_id = aws_kms_key.tokens.key_id
}

data "aws_iam_policy_document" "kms" {
  statement {
    actions = [
      "kms:Encrypt",
      "kms:Decrypt",
    ]

    resources = [
      aws_kms_key.tokens.arn
    ]
  }
}