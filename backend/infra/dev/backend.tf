terraform {
  backend "s3" {
    bucket = "narrative-terraform-state"
    key = "dev/facebook-connector-api/terraform.tfstate"
    region = "us-east-1"
    dynamodb_table = "terraform-locks"
  }
}
