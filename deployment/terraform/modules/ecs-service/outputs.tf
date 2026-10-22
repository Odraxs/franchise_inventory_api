output "cluster_name" {
  description = "Name of the ECS cluster."
  value       = aws_ecs_cluster.this.name
}

output "service_name" {
  description = "Name of the ECS service. Null until an image is published (container_image is null)."
  value       = one(aws_ecs_service.this[*].name)
}

output "log_group_name" {
  description = "Name of the CloudWatch log group that holds the container logs."
  value       = aws_cloudwatch_log_group.this.name
}

output "task_security_group_id" {
  description = "ID of the task security group. The database security group accepts PostgreSQL only from it."
  value       = aws_security_group.task.id
}

output "execution_role_arn" {
  description = "ARN of the task execution role (ECR pull and log writes)."
  value       = aws_iam_role.execution.arn
}

output "task_role_arn" {
  description = "ARN of the task role (read the DB Secret)."
  value       = aws_iam_role.task.arn
}

output "execution_role_policy" {
  description = "Planned inline policy of the execution role, decoded. Exposed so tests can assert its scope."
  value       = jsondecode(aws_iam_role_policy.execution.policy)
}

output "task_role_policy" {
  description = "Planned inline policy of the task role, decoded. Exposed so tests can assert its scope."
  value       = jsondecode(aws_iam_role_policy.task.policy)
}

output "assume_role_policies" {
  description = "Planned trust policies of the execution and task roles, decoded. Exposed so tests can assert them."
  value = {
    execution = jsondecode(aws_iam_role.execution.assume_role_policy)
    task      = jsondecode(aws_iam_role.task.assume_role_policy)
  }
}

output "log_retention_days" {
  description = "Planned retention of the log group in days. Exposed so tests can assert it."
  value       = aws_cloudwatch_log_group.this.retention_in_days
}

output "cluster_settings" {
  description = "Planned cluster settings and capacity providers. Exposed so tests can assert them."
  value = {
    container_insights = one([for s in aws_ecs_cluster.this.setting : s.value if s.name == "containerInsights"])
    capacity_providers = aws_ecs_cluster_capacity_providers.this.capacity_providers
  }
}

output "task_security_group_rules" {
  description = "Planned rules of the task security group. Exposed so tests can assert them."
  value = {
    ingress = {
      from_port                = aws_vpc_security_group_ingress_rule.from_alb.from_port
      to_port                  = aws_vpc_security_group_ingress_rule.from_alb.to_port
      source_security_group_id = aws_vpc_security_group_ingress_rule.from_alb.referenced_security_group_id
    }
    egress = [
      for rule in [aws_vpc_security_group_egress_rule.https, aws_vpc_security_group_egress_rule.postgres] : {
        port                          = rule.from_port
        cidr_ipv4                     = rule.cidr_ipv4
        destination_security_group_id = rule.referenced_security_group_id
      }
    ]
  }
}

output "task_definition" {
  description = "Planned task definition with its container definitions decoded. Null until an image is published. Exposed so tests can assert it."
  value = one([
    for td in aws_ecs_task_definition.this : {
      cpu                      = td.cpu
      memory                   = td.memory
      network_mode             = td.network_mode
      requires_compatibilities = td.requires_compatibilities
      operating_system_family  = one(td.runtime_platform).operating_system_family
      cpu_architecture         = one(td.runtime_platform).cpu_architecture
      containers               = jsondecode(td.container_definitions)
    }
  ])
}

output "service_settings" {
  description = "Planned settings of the ECS service. Null until an image is published. Exposed so tests can assert them."
  value = one([
    for svc in aws_ecs_service.this : {
      desired_count                      = svc.desired_count
      capacity_providers                 = [for s in svc.capacity_provider_strategy : s.capacity_provider]
      subnets                            = one(svc.network_configuration).subnets
      security_groups                    = one(svc.network_configuration).security_groups
      assign_public_ip                   = one(svc.network_configuration).assign_public_ip
      target_group_arn                   = one(svc.load_balancer).target_group_arn
      container_name                     = one(svc.load_balancer).container_name
      container_port                     = one(svc.load_balancer).container_port
      health_check_grace_period_seconds  = svc.health_check_grace_period_seconds
      deployment_minimum_healthy_percent = svc.deployment_minimum_healthy_percent
      deployment_maximum_percent         = svc.deployment_maximum_percent
      circuit_breaker_enable             = one(svc.deployment_circuit_breaker).enable
      circuit_breaker_rollback           = one(svc.deployment_circuit_breaker).rollback
      enable_execute_command             = svc.enable_execute_command
    }
  ])
}

output "autoscaling" {
  description = "Planned Service Auto Scaling target and CPU policy. Null until an image is published. Exposed so tests can assert them."
  value = length(aws_appautoscaling_target.this) + length(aws_appautoscaling_policy.cpu) == 0 ? null : {
    service_namespace      = one(aws_appautoscaling_target.this[*].service_namespace)
    resource_id            = one(aws_appautoscaling_target.this[*].resource_id)
    scalable_dimension     = one(aws_appautoscaling_target.this[*].scalable_dimension)
    min_capacity           = one(aws_appautoscaling_target.this[*].min_capacity)
    max_capacity           = one(aws_appautoscaling_target.this[*].max_capacity)
    policy_type            = one(aws_appautoscaling_policy.cpu[*].policy_type)
    target_value           = one(flatten(aws_appautoscaling_policy.cpu[*].target_tracking_scaling_policy_configuration[*].target_value))
    predefined_metric_type = one(flatten(aws_appautoscaling_policy.cpu[*].target_tracking_scaling_policy_configuration[*].predefined_metric_specification[*].predefined_metric_type))
    scale_out_cooldown     = one(flatten(aws_appautoscaling_policy.cpu[*].target_tracking_scaling_policy_configuration[*].scale_out_cooldown))
    scale_in_cooldown      = one(flatten(aws_appautoscaling_policy.cpu[*].target_tracking_scaling_policy_configuration[*].scale_in_cooldown))
    disable_scale_in       = one(flatten(aws_appautoscaling_policy.cpu[*].target_tracking_scaling_policy_configuration[*].disable_scale_in))
  }
}
