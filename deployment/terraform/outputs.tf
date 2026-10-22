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
