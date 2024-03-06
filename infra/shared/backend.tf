terraform {
  backend "s3" {
    bucket         = "narrative-terraform-state"
    key            = "facebook-connector/shared/terraform.tfstate"
    region         = "us-east-1"
    dynamodb_table = "terraform-locks"
  }
}
