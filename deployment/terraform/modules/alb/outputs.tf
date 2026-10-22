output "dns_name" {
  description = "Public DNS name of the ALB."
  value       = aws_lb.this.dns_name
}

output "target_group_arn" {
  description = "ARN of the target group. The ECS service registers its tasks here."
  value       = aws_lb_target_group.api.arn
}

output "security_group_id" {
  description = "ID of the ALB security group. The task security group accepts traffic only from it."
  value       = aws_security_group.alb.id
}

output "health_check" {
  description = "Planned health check of the target group. Exposed so tests can assert it."
  value = {
    path     = one(aws_lb_target_group.api.health_check).path
    matcher  = one(aws_lb_target_group.api.health_check).matcher
    port     = one(aws_lb_target_group.api.health_check).port
    protocol = one(aws_lb_target_group.api.health_check).protocol
  }
}

output "target_group_settings" {
  description = "Planned target type and port of the target group. Exposed so tests can assert them."
  value = {
    target_type = aws_lb_target_group.api.target_type
    port        = aws_lb_target_group.api.port
    protocol    = aws_lb_target_group.api.protocol
  }
}

output "ingress_rules" {
  description = "Ingress rules of the ALB security group as a set of {cidr, port}. Exposed so tests can assert them."
  value       = toset([for rule in aws_vpc_security_group_ingress_rule.listener : { cidr = rule.cidr_ipv4, port = rule.from_port }])
}

output "listeners" {
  description = "Planned listeners as {port, protocol, default_action}. Exposed so tests can assert them."
  value = [
    for listener in concat([aws_lb_listener.http], aws_lb_listener.https) : {
      port           = listener.port
      protocol       = listener.protocol
      default_action = one(listener.default_action).type
    }
  ]
}

output "actuator_rule" {
  description = "Planned listener rule that hides the actuator. Exposed so tests can assert it."
  value = {
    listener_port = local.traffic_listener.port
    priority      = aws_lb_listener_rule.block_actuator.priority
    action_type   = one(aws_lb_listener_rule.block_actuator.action).type
    status_code   = one(one(aws_lb_listener_rule.block_actuator.action).fixed_response).status_code
    path_patterns = one(one(aws_lb_listener_rule.block_actuator.condition).path_pattern).values
  }
}
