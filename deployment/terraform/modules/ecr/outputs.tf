output "repository_url" {
  description = "URL of the ECR repository (<account>.dkr.ecr.<region>.amazonaws.com/<name>). It is the image name without a tag."
  value       = aws_ecr_repository.this.repository_url
}

output "repository_name" {
  description = "Name of the ECR repository."
  value       = aws_ecr_repository.this.name
}

output "image_tag_mutability" {
  description = "Tag mutability of the repository. Always IMMUTABLE; exposed so tests can assert it."
  value       = aws_ecr_repository.this.image_tag_mutability
}

output "repository_arn" {
  description = "ARN of the ECR repository. Use it to scope IAM pull permissions."
  value       = aws_ecr_repository.this.arn
}
