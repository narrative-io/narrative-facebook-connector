variable "app_port" {
  type = number
}

variable "name_prefix" {
  type = string
}

variable "private_subnet_ids" {
  type = list(string)
}

variable "stage" {
  type = string
}

variable "vpc_id" {
  type = string
}
