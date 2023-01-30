locals {
  container_name = "${var.name_prefix}-${var.stage}"
}

variable "app_port" {
  type = number
}

variable "aws_iam_role_arn" {
  type = string
}

variable "aws_lb_target_group_arn" {
  type = string
}

variable "cpu" {
  type = string
}

variable "desired_count" {
  type = number
  description = "The number of servers to run"
}

variable "ephemeral_storage" {
  type = number
}

variable "image_tag" {
  type = string
}

variable "name_prefix" {
  type = string
}

variable "private_subnet_ids" {
  type = list(string)
}

variable "memory" {
  type = string
}

variable "repository_name" {
  type = string
}

variable "security_group_id" {
  type = string
}

variable "ssm_db_password" {
  type = string
  description = "The SSM path for the db password"
}

variable "ssm_db_username" {
  type = string
  description = "The SSM path for the db username"
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

