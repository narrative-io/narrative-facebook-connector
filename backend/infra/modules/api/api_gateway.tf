resource "aws_api_gateway_rest_api" "_" {
  name = "${var.name_prefix}-${var.stage}"
  endpoint_configuration {
    types = [var.is_public ? "EDGE":"PRIVATE"]
  }
  lifecycle {
    # Workaround this issue:
    # https://github.com/terraform-providers/terraform-provider-aws/issues/5549
    # I have to taint the resource if I want to change the policy
    ignore_changes = [
      policy
    ]
  }
}

resource "aws_api_gateway_rest_api_policy" "_" {
  rest_api_id = aws_api_gateway_rest_api._.id

  policy = <<EOF
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Principal": "*",
    "Action": "execute-api:Invoke",
    "Resource": "${aws_api_gateway_rest_api._.execution_arn}/*"
  }]
}
EOF
}

resource "aws_api_gateway_resource" "_" {
  rest_api_id = aws_api_gateway_rest_api._.id
  parent_id = aws_api_gateway_rest_api._.root_resource_id
  path_part = "{proxy+}"
}

resource "aws_api_gateway_method" "any" {
  rest_api_id   = aws_api_gateway_rest_api._.id
  resource_id   = aws_api_gateway_resource._.id
  authorization = "NONE"
  http_method   = "ANY"

  request_parameters = {
    "method.request.path.proxy" = true
  }

}

resource "aws_api_gateway_integration" "_" {
  rest_api_id = aws_api_gateway_rest_api._.id
  resource_id = aws_api_gateway_resource._.id
  http_method = aws_api_gateway_method.any.http_method
  integration_http_method = "ANY"
  uri = "http://${data.aws_lb._.dns_name}:${var.app_port}/{proxy}"
  type = "HTTP_PROXY"
  connection_type = "VPC_LINK"
  connection_id = aws_api_gateway_vpc_link._.id

  request_parameters = {
    "integration.request.path.proxy" = "method.request.path.proxy"
  }
}

data "aws_lb" "_" {
  arn = var.aws_lb_arn
}

resource "aws_api_gateway_vpc_link" "_" {
  name = "${var.name_prefix} to nlb (${var.stage})"
  target_arns = [data.aws_lb._.arn]
}

// The gateway needs a stage to be deployed to
// We're not using that feature because it's not so practical to manage. So we have one gateway per stage containing only that stage
resource "aws_api_gateway_stage" "_" {
  stage_name = var.api_version
  rest_api_id = aws_api_gateway_rest_api._.id
  deployment_id = aws_api_gateway_deployment._.id

  access_log_settings {
    destination_arn = aws_cloudwatch_log_group._.arn
    format = file("${path.module}/access_log_format.json")
  }

  depends_on = [aws_api_gateway_method.any]

}

// The Cloudwatch access log group for the stage, which logs incoming requests.
resource "aws_cloudwatch_log_group" "_" {
  name = "${var.name_prefix}-api_gateway_log_group-${var.stage}"
  retention_in_days = 90
}

// And this is the deployment to the stage.
resource "aws_api_gateway_deployment" "_" {
  rest_api_id = aws_api_gateway_rest_api._.id

  lifecycle {
    create_before_destroy = true
  }

  variables = {
    redeploy = var.api_image_tag
  }
}

output "aws_api_gateway_id" {
  value = aws_api_gateway_rest_api._.id
}

output "aws_api_gateway_stage_name" {
  value = aws_api_gateway_stage._.stage_name
}
