resource "aws_ecs_cluster" "_" {
  name = "${var.name_prefix}-${var.stage}"
  capacity_providers = ["FARGATE", "FARGATE_SPOT"]

  setting {
    name  = "containerInsights"
    value = "enabled"
  }

  tags = {}
}