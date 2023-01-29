resource "aws_lb" "_" {
  name = "${var.name_prefix}-lb-${var.stage}"
  internal = true
  load_balancer_type = "network"
  subnets  = var.private_subnet_ids
  enable_deletion_protection = false
  tags = {}

}

resource "aws_lb_listener" "nlb_80" {
  load_balancer_arn = aws_lb._.arn
  port = var.app_port
  protocol = "TCP"

  default_action {
    type = "forward"
    target_group_arn = aws_lb_target_group._.arn
  }
}

resource "aws_lb_target_group" "_" {
  port = var.app_port
  protocol = "TCP"
  target_type = "ip"
  vpc_id = var.vpc_id

  lifecycle {
    create_before_destroy = true
  }
}

output "aws_lb_arn" {
  value = aws_lb._.arn
}

output "aws_lb_target_group_arn" {
  value = aws_lb_target_group._.arn
}