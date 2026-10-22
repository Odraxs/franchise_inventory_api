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

# Same account and region. Used only to generate the ephemeral DB master password
# (modules/database/password), which creates no resource and so needs no tags.
# It is a separate configuration because terraform test cannot mock ephemeral
# resources: the tests mock the default provider and give this one an offline setup.
provider "aws" {
  alias  = "db_password"
  region = var.aws_region
}
