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
