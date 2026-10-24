terraform {
  # 1.11 is the first version with write-only arguments (password_wo, secret_string_wo).
  required_version = ">= 1.11, < 2.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"

      # aws.ephemeral generates the ephemeral master password (see ../password/main.tf).
      configuration_aliases = [aws.ephemeral]
    }
  }
}
