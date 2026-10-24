output "state_bucket" {
  description = "Name of the S3 bucket that holds the Remote State. Pass it to the Root Composition at terraform init."
  value       = aws_s3_bucket.state.bucket
}
