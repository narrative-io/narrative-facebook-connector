module "facebook_connector_api" {
  source = "git::git@github.com:narrative-io/narrative-network-infra.git//ecr-app-repository"
  name   = "narrative-facebook-connector/api"
}

module "facebook_connector_worker" {
  source = "git::git@github.com:narrative-io/narrative-network-infra.git//ecr-app-repository"
  name   = "narrative-facebook-connector/worker"
}
