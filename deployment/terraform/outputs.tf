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
