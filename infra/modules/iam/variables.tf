variable "name_prefix" {
  type = string
}

variable "stage" {
  type = string
}

variable "ssm_key_alias" {
  type = string
  description = "The alias of the ssm encryption key"
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
