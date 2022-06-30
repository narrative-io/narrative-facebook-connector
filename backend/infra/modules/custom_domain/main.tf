variable "domain_name" {
  type = string
}

variable "api_gateway_id" {
  type = string
}

variable "api_gateway_stage_name" {
  type = string
}

resource "aws_api_gateway_domain_name" "_" {
  certificate_arn = "arn:aws:acm:us-east-1:704349335716:certificate/6e3eb3ce-ae10-4eaa-a716-ac3d4c00f9f2"
  domain_name  = var.domain_name
}

resource "aws_route53_record" "example" {
  name = aws_api_gateway_domain_name._.domain_name
  type = "A"
  zone_id = "Z0056931U8JZGZ8NQ5YJ"

  alias {
    evaluate_target_health = true
    name = aws_api_gateway_domain_name._.cloudfront_domain_name
    zone_id = aws_api_gateway_domain_name._.cloudfront_zone_id
  }
}

resource "aws_api_gateway_base_path_mapping" "_" {
  api_id = var.api_gateway_id
  domain_name = aws_api_gateway_domain_name._.domain_name
  stage_name = var.api_gateway_stage_name
}
