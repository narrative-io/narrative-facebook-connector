resource "aws_ecs_service" "_" {
  name = "${var.name_prefix}-service-${var.stage}"
  cluster = aws_ecs_cluster._.id
  task_definition = aws_ecs_task_definition._.arn
  desired_count = var.desired_count
  # Give the container some time to start up before having ELB assess its health.
  # Our current server start up time is long enough that having a health check grace period of 0 seconds means instances
  # sometimes get marked as unhealthy before they've had a chance to come up properly.
  health_check_grace_period_seconds = 60

  load_balancer {
    container_name = local.container_name
    container_port = var.app_port
    target_group_arn = var.aws_lb_target_group_arn
  }

  network_configuration {
    security_groups = [
      var.security_group_id
    ]
    subnets = var.private_subnet_ids
    assign_public_ip = false
  }

  launch_type = "FARGATE"

  enable_execute_command = true

  tags = {}
}
