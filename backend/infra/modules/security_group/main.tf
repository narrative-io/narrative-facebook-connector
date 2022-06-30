variable "name" {
  type = string
}

variable "vpc_id" {
  type = string
}

resource "aws_security_group" "_" {
  name = var.name
  vpc_id = var.vpc_id

  ingress {
    from_port = 0
    to_port = 0
    protocol = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    from_port = 0
    to_port = 0
    protocol = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

output "aws_security_group_id" {
  value = aws_security_group._.id
}