# Credentials come only from the environment (AWS_PROFILE or the default chain).
# Never set "profile" here, so the code names no personal or company profile.
provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project     = local.project
      Environment = var.environment
      ManagedBy   = "terraform"
      Repository  = var.repository
    }
  }
}
