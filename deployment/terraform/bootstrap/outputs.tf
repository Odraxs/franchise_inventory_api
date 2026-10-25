output "state_bucket" {
  description = "Name of the S3 bucket that holds the Remote State. Pass it to the Root Composition at terraform init."
  value       = aws_s3_bucket.state.bucket
}

output "deployer_user_name" {
  description = "Name of the IAM user of the CD pipeline. Create its access key with aws iam create-access-key --user-name <name>."
  value       = aws_iam_user.deployer.name
}

output "deployer_user_arn" {
  description = "ARN of the IAM user of the CD pipeline."
  value       = aws_iam_user.deployer.arn
}

output "role_boundary_arn" {
  description = "ARN of the permissions boundary that every project IAM role must carry."
  value       = aws_iam_policy.role_boundary.arn
}
