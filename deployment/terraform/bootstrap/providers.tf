# Credentials and profile come from the environment (AWS_PROFILE).
provider "aws" {
  region = var.region

  default_tags {
    tags = {
      Project    = var.project
      ManagedBy  = "terraform"
      Repository = var.repository
    }
  }
}
