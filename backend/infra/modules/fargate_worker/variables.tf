locals {
  container_name = "${var.name_prefix}-${var.stage}"
}

variable "aws_iam_role_arn" {
  type = string
}

variable "cpu" {
  type = string
}

variable "image_tag" {
  type = string
}

variable "desired_count" {
  type = number
  description = "The number of servers to run"
}

variable "ephemeral_storage" {
  type = number
}

variable "memory" {
  type = string
}

variable "name_prefix" {
  type = string
}

variable "private_subnet_ids" {
  type = list(string)
}

variable "repository_name" {
  type = string
}

variable "security_group_id" {
  type = string
}

variable "ssm_db_username" {
  type = string
  description = "The SSM path for the db username"
}

variable "ssm_db_password" {
  type = string
  description = "The SSM path for the db password"
}

variable "ssm_narrative_api_client" {
  type = string
  description = "The SSM path where the openapi client id is stored"
}

variable "ssm_narrative_api_secret" {
  type = string
  description = "The SSM path where the openapi secret is stored"
}

variable "stage" {
  type = string
}

variable "token_kms_key_id" {
  type = string
  description = "ID of the KMS key used to encrypt tokens"
}

variable "vpc_id" {
  type = string
}
