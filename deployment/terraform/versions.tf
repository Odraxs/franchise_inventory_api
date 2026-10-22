terraform {
  # 1.11 is the first version with write-only arguments (used later for the DB password).
  required_version = ">= 1.11, < 2.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
  }

  # Partial configuration. envs/<env>.backend.hcl sets key, region, encrypt and
  # use_lockfile. The state bucket is never committed: pass it at init time, e.g.
  #   terraform init -backend-config=envs/dev.backend.hcl -backend-config="bucket=<state-bucket>"
  backend "s3" {}
}
