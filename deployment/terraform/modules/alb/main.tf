# Internet-facing Application Load Balancer: the single public HTTP entry point
# of one Environment. It forwards traffic to the ECS tasks (target type ip) and
# never exposes the actuator endpoints to the internet.
#
# certificate_arn empty: one HTTP listener on port 80 forwards to the tasks.
# certificate_arn set:   HTTP on port 80 only redirects to HTTPS on port 443,
#                        and the HTTPS listener forwards to the tasks.

locals {
  https_enabled = var.certificate_arn != ""

  listener_ports = local.https_enabled ? [80, 443] : [80]

  # One ingress rule per (CIDR, port) pair. The keys are known at plan time.
  ingress_rules = {
    for pair in setproduct(var.allowed_ingress_cidrs, local.listener_ports) :
    "${pair[1]}-${pair[0]}" => { cidr = pair[0], port = pair[1] }
  }

  # Spring Boot serves /actuator (the endpoint list) and /actuator/** (health,
  # prometheus). Both must stay internal.
  actuator_path_patterns = ["/actuator", "/actuator/*"]
}

resource "aws_security_group" "alb" {
  name        = "${var.name_prefix}-alb"
  description = "Internet access to the ${var.name_prefix} ALB"
  vpc_id      = var.vpc_id

  tags = {
    Name = "${var.name_prefix}-alb"
  }
}

resource "aws_vpc_security_group_ingress_rule" "listener" {
  for_each = local.ingress_rules

  security_group_id = aws_security_group.alb.id
  description       = "Port ${each.value.port} from ${each.value.cidr}"
  ip_protocol       = "tcp"
  from_port         = each.value.port
  to_port           = each.value.port
  cidr_ipv4         = each.value.cidr
}

# The ALB opens connections only to its targets (traffic and health checks),
# which are ECS tasks inside the VPC on the container port. The VPC CIDR keeps
# this module independent of the task security group, which is created later
# and itself accepts traffic only from this security group.
resource "aws_vpc_security_group_egress_rule" "to_targets" {
  security_group_id = aws_security_group.alb.id
  description       = "Container port of the targets inside the VPC"
  ip_protocol       = "tcp"
  from_port         = var.container_port
  to_port           = var.container_port
  cidr_ipv4         = var.vpc_cidr
}

resource "aws_lb" "this" {
  name               = "${var.name_prefix}-alb"
  internal           = false
  load_balancer_type = "application"
  security_groups    = [aws_security_group.alb.id]
  subnets            = var.public_subnet_ids

  # Drop requests with header names that are not valid HTTP (request smuggling).
  drop_invalid_header_fields = true

  tags = {
    Name = "${var.name_prefix}-alb"
  }
}

resource "aws_lb_target_group" "api" {
  name        = "${var.name_prefix}-api"
  port        = var.container_port
  protocol    = "HTTP"
  target_type = "ip"
  vpc_id      = var.vpc_id

  # The app drains in-flight requests quickly. The default 300 s slows every deployment.
  deregistration_delay = 30

  # Health checks go straight to each target on the container port. They do not
  # pass through the listener, so the actuator 404 rule does not affect them.
  health_check {
    enabled             = true
    protocol            = "HTTP"
    port                = "traffic-port"
    path                = var.health_check_path
    matcher             = "200"
    interval            = 15
    timeout             = 5
    healthy_threshold   = 2
    unhealthy_threshold = 3
  }

  tags = {
    Name = "${var.name_prefix}-api"
  }
}

resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.this.arn
  port              = 80
  protocol          = "HTTP"

  default_action {
    type             = local.https_enabled ? "redirect" : "forward"
    target_group_arn = local.https_enabled ? null : aws_lb_target_group.api.arn

    dynamic "redirect" {
      for_each = local.https_enabled ? [1] : []

      content {
        protocol    = "HTTPS"
        port        = "443"
        status_code = "HTTP_301"
      }
    }
  }
}

resource "aws_lb_listener" "https" {
  count = local.https_enabled ? 1 : 0

  load_balancer_arn = aws_lb.this.arn
  port              = 443
  protocol          = "HTTPS"
  ssl_policy        = var.ssl_policy
  certificate_arn   = var.certificate_arn

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.api.arn
  }
}

# Answers 404 for the actuator paths on the listener that forwards traffic
# (HTTPS when it exists, otherwise HTTP). With HTTPS, an HTTP request is first
# redirected and then gets the same 404.
locals {
  traffic_listener = local.https_enabled ? aws_lb_listener.https[0] : aws_lb_listener.http
}

resource "aws_lb_listener_rule" "block_actuator" {
  listener_arn = local.traffic_listener.arn
  priority     = 1

  action {
    type = "fixed-response"

    fixed_response {
      content_type = "text/plain"
      status_code  = "404"
      message_body = "Not Found"
    }
  }

  condition {
    path_pattern {
      values = local.actuator_path_patterns
    }
  }

  tags = {
    Name = "${var.name_prefix}-block-actuator"
  }
}
