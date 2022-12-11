locals {
  app_port                 = 8080
  api_image_tag            = "0.0.15"
  api_image_repository     = "narrative-facebook-connector/api"
  domain_name              = "facebook-dev.narrativeconnectors.com"
  name_prefix              = "facebook-connector"
  ssm_db_username          = "/${local.stage}/connectors/facebook/api/facebookconnector-db/user"
  ssm_db_password          = "/${local.stage}/connectors/facebook/api/facebookconnector-db/password"
  ssm_narrative_api_client = "/${local.stage}/connectors/facebook/openapi/client"
  ssm_narrative_api_secret = "/${local.stage}/connectors/facebook/openapi/secret"
  worker_image_tag         = "0.0.15"
  worker_image_repository  = "narrative-facebook-connector/worker"
  stage                    = "dev"
}

data "aws_vpc" "main" {
  filter {
    name   = "tag:Name"
    values = ["main-${local.stage}"]
  }
}

data "aws_subnets" "private" {
  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.main.id]
  }

  tags = {
    Tier = "private"
  }
}


module "alerts-api" {
  source         = "../modules/alerts"
  stage          = local.stage
  filter_name    = "alerts"
  log_group_name = module.fargate_api.log_group_name
  log_group_arn  = module.fargate_api.log_group_arn
}

module "alerts-worker" {
  source         = "../modules/alerts"
  stage          = local.stage
  filter_name    = "alerts"
  log_group_name = module.fargate_worker.log_group_name
  log_group_arn  = module.fargate_worker.log_group_arn
}

data "aws_ssm_parameter" "datadog_api_key" {
  name            = "/shared/datadog/api-key"
  with_decryption = true
}

data "aws_ssm_parameter" "datadog_app_key" {
  name            = "/shared/datadog/app-key"
  with_decryption = true
}

module "api_gateway" {
  source        = "../modules/api"
  api_image_tag = local.api_image_tag
  api_version   = "v1"
  app_port      = local.app_port
  aws_lb_arn    = module.load_balancer.aws_lb_arn
  is_public     = true
  name_prefix   = local.name_prefix
  stage         = local.stage
  vpc_id        = data.aws_vpc.main.id
}

module "custom_domain" {
  source                 = "../modules/custom_domain"
  domain_name            = local.domain_name
  api_gateway_id         = module.api_gateway.aws_api_gateway_id
  api_gateway_stage_name = module.api_gateway.aws_api_gateway_stage_name
}

module "fargate_api" {
  source                  = "../modules/fargate_api"
  app_port                = local.app_port
  aws_lb_target_group_arn = module.load_balancer.aws_lb_target_group_arn
  aws_iam_role_arn        = module.iam.aws_iam_role_arn
  cpu                     = "1024"
  desired_count           = 1
  ephemeral_storage       = 128
  image_tag               = local.api_image_tag
  memory                  = "8192"
  name_prefix             = "${local.name_prefix}-api"
  private_subnet_ids      = data.aws_subnets.private.ids
  repository_name         = local.api_image_repository
  security_group_id       = module.security_group.aws_security_group_id
  ssm_db_username         = local.ssm_db_username
  ssm_db_password         = local.ssm_db_password
  stage                   = local.stage
  token_kms_key_id        = module.iam.token_kms_key_id
  vpc_id                  = data.aws_vpc.main.id
}

module "fargate_worker" {
  source                   = "../modules/fargate_worker"
  aws_iam_role_arn         = module.iam.aws_iam_role_arn
  cpu                      = "1024"
  desired_count            = 1
  ephemeral_storage        = 128
  image_tag                = local.worker_image_tag
  memory                   = "8192"
  name_prefix              = "${local.name_prefix}-worker"
  private_subnet_ids       = data.aws_subnets.private.ids
  repository_name          = local.worker_image_repository
  security_group_id        = module.security_group.aws_security_group_id
  ssm_db_username          = local.ssm_db_username
  ssm_db_password          = local.ssm_db_password
  ssm_narrative_api_client = local.ssm_narrative_api_client
  ssm_narrative_api_secret = local.ssm_narrative_api_secret
  stage                    = local.stage
  token_kms_key_id         = module.iam.token_kms_key_id
  vpc_id                   = data.aws_vpc.main.id
}

module "iam" {
  source                   = "../modules/iam"
  name_prefix              = local.name_prefix
  ssm_key_alias            = "alias/ssm-params-${local.stage}"
  ssm_db_username          = local.ssm_db_username
  ssm_db_password          = local.ssm_db_password
  ssm_narrative_api_client = local.ssm_narrative_api_client
  ssm_narrative_api_secret = local.ssm_narrative_api_secret
  stage                    = local.stage
}

module "load_balancer" {
  source             = "../modules/lb"
  app_port           = local.app_port
  name_prefix        = local.name_prefix
  private_subnet_ids = data.aws_subnets.private.ids
  stage              = local.stage
  vpc_id             = data.aws_vpc.main.id
}

module "security_group" {
  source = "../modules/security_group"
  name   = "${local.name_prefix}-${local.stage}"
  vpc_id = data.aws_vpc.main.id
}
