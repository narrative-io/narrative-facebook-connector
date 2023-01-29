resource "aws_ecs_cluster" "_" {
  name = "${var.name_prefix}-${var.stage}"

  setting {
    name  = "containerInsights"
    value = "enabled"
  }

  tags = {}
}

resource "aws_ecs_cluster_capacity_providers" "_" {
  cluster_name = aws_ecs_cluster._.name
  capacity_providers = ["FARGATE", "FARGATE_SPOT"]
}

