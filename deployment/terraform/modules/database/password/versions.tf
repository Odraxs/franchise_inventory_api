terraform {
  # 1.10 is the first version with ephemeral resources; 1.11 matches the other modules.
  required_version = ">= 1.11, < 2.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
  }
}
