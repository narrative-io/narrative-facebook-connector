output "aws_iam_role_arn" {
  value = aws_iam_role._.arn
}

output "token_kms_key_id" {
  value = aws_kms_key.tokens.key_id
}
