output "ecr_repository_url" {
  description = "URL of the Environment's ECR repository. The publish script pushes images here."
  value       = module.ecr.repository_url
}

output "ecr_repository_name" {
  description = "Name of the Environment's ECR repository."
  value       = module.ecr.repository_name
}

output "container_image" {
  description = "Full image reference (<repository_url>:<image_tag>) that the Environment deploys. Null when image_tag is not set."
  value       = local.container_image
}

output "alb_url" {
  description = "Base URL of the API through the ALB: https://<dns> when a certificate is set, otherwise http://<dns>."
  value       = local.alb_url
}

output "db_secret_name" {
  description = "Name of the DB Secret. The ECS task gets it in the DB_SECRET_NAME environment variable."
  value       = module.database.secret_name
}

output "api_secret_name" {
  description = "Name of the API Secret that holds the API Credential. The ECS task gets it in the API_SECRET_NAME environment variable."
  value       = module.api_secret.secret_name
}

output "ecs_cluster_name" {
  description = "Name of the ECS cluster, e.g. for aws ecs describe-services --cluster."
  value       = module.ecs_service.cluster_name
}

output "ecs_service_name" {
  description = "Name of the ECS service. Null until image_tag is set (the service needs a published image)."
  value       = module.ecs_service.service_name
}

output "ecs_task_definition_arn" {
  description = "ARN of the ECS task definition, including its revision, that the Environment deploys. Null until image_tag is set."
  value       = module.ecs_service.task_definition_arn
}

output "ecs_log_group_name" {
  description = "CloudWatch log group of the API container, e.g. for aws logs tail --follow."
  value       = module.ecs_service.log_group_name
}
