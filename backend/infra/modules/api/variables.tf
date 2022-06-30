variable "name_prefix" {
  type = string
}

variable "stage" {
  type = string
}

variable "app_port" {
  type = number
}

variable "vpc_id" {
  type = string
}

variable "is_public" {
  type = bool
}

variable "api_version" {
  type = string
}

variable "aws_lb_arn" {
  type = string
}

variable "api_image_tag" {
  type = string
}
