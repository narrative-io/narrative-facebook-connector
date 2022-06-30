resource "aws_ecs_task_definition" "_" {
  family = "${var.name_prefix}-${var.stage}"
  network_mode  = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  # https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task_definition_parameters.html
  cpu = var.cpu
  memory = var.memory
  task_role_arn = var.aws_iam_role_arn
  execution_role_arn = var.aws_iam_role_arn
  ephemeral_storage {
    size_in_gib = var.ephemeral_storage
  }
  container_definitions = <<TASK_DEFINITION
[
  {
    "name": "${local.container_name}",
    "image": "${data.aws_ecr_repository._.repository_url}:${data.aws_ecr_image._.image_tag}",
    "essential": true,
    "environment": [
      { "name": "SSM_DB_USERNAME", "value": "${var.ssm_db_username}" },
      { "name": "SSM_DB_PASSWORD", "value": "${var.ssm_db_password}" },
      { "name": "STAGE", "value": "${var.stage}" },
      { "name": "TOKEN_KMS_KEY_ID", "value" ${var.token_kms_key_id}" }
    ],
    "portMappings": [
      {
        "containerPort": ${var.app_port},
        "protocol": "tcp"
      }
    ],
    "networkMode": "awsvpc",
    "logConfiguration": {
      "logDriver": "awslogs",
      "options": {
        "awslogs-group": "${aws_cloudwatch_log_group.task.name}",
        "awslogs-region": "us-east-1",
        "awslogs-stream-prefix": "web"
      }
    }
  }
]
TASK_DEFINITION
}

resource "aws_cloudwatch_log_group" "task" {
  name = "${var.name_prefix}-fargate_task_log_group-${var.stage}"
  retention_in_days = 30
}

data "aws_ecr_repository" "_" {
  name = var.repository_name
}

data "aws_ecr_image" "_" {
  repository_name = var.repository_name
  image_tag = var.image_tag
}

output "log_group_name" {
  value = aws_cloudwatch_log_group.task.name
}

output "log_group_arn" {
  value = aws_cloudwatch_log_group.task.arn
}
