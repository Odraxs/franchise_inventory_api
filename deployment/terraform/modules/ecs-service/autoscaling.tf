# Service Auto Scaling: Application Auto Scaling owns the desired count of the
# service after the first apply. It exists only together with the service
# (local.service_count), so nothing is planned while no image is published.
#
# Terraform does not fight the scaler: aws_ecs_service.this ignores changes to
# desired_count (see its lifecycle block), so a later plan never resets the task
# count that the scaler has set.
#
# The first registration creates the service-linked role
# AWSServiceRoleForApplicationAutoScaling_ECSService if it does not exist yet, so
# no role_arn is set here.

resource "aws_appautoscaling_target" "this" {
  count = local.service_count

  service_namespace  = "ecs"
  resource_id        = "service/${aws_ecs_cluster.this.name}/${aws_ecs_service.this[0].name}"
  scalable_dimension = "ecs:service:DesiredCount"
  min_capacity       = var.autoscaling_min_capacity
  max_capacity       = var.autoscaling_max_capacity
}

# Target tracking keeps the average CPU of the service tasks near the target. It
# uses the basic ECS service metric (CPUUtilization in AWS/ECS), so Container
# Insights can stay off.
resource "aws_appautoscaling_policy" "cpu" {
  count = local.service_count

  name               = "${var.name_prefix}-api-cpu-target-tracking"
  policy_type        = "TargetTrackingScaling"
  service_namespace  = aws_appautoscaling_target.this[0].service_namespace
  resource_id        = aws_appautoscaling_target.this[0].resource_id
  scalable_dimension = aws_appautoscaling_target.this[0].scalable_dimension

  target_tracking_scaling_policy_configuration {
    target_value       = var.autoscaling_cpu_target
    scale_out_cooldown = var.autoscaling_scale_out_cooldown_seconds
    scale_in_cooldown  = var.autoscaling_scale_in_cooldown_seconds
    disable_scale_in   = false

    predefined_metric_specification {
      predefined_metric_type = "ECSServiceAverageCPUUtilization"
    }
  }
}
