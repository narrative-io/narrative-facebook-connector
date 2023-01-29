provider "aws" {
  region  = "us-east-1"
  default_tags {}
}

provider "datadog" {
  api_key = var.datadog_api_key
  app_key = var.datadog_app_key
}

variable "datadog_api_key" {
  type = string
}

variable "datadog_app_key" {
  type = string
}
