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

# Same account and region. Used only to generate the ephemeral passwords (the DB
# master password and the API Credential password, both through modules/password),
# which create no resource and so need no tags. It is a separate configuration
# because terraform test cannot mock ephemeral resources: the tests mock the
# default provider and give this one an offline setup.
provider "aws" {
  alias  = "ephemeral"
  region = var.aws_region
}
