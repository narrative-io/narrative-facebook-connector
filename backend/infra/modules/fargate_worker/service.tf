resource "aws_ecs_service" "_" {
  name = "${var.name_prefix}-service-${var.stage}"
  cluster = aws_ecs_cluster._.id
  task_definition = aws_ecs_task_definition._.arn

  desired_count = var.desired_count

  # essentially stops the current running version before starting a new one
  deployment_minimum_healthy_percent = 0
  deployment_maximum_percent = 100


  network_configuration {
    security_groups = [var.security_group_id]
    subnets = var.private_subnet_ids
    assign_public_ip = false
  }
  launch_type = "FARGATE"

  enable_execute_command = true

  tags = {}
}
