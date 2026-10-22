# Partial S3 backend configuration for dev. The bucket is passed at init:
#   terraform init -backend-config=envs/dev.backend.hcl -backend-config="bucket=<state-bucket>"
key          = "franchise-inventory/dev/terraform.tfstate"
region       = "us-east-1"
encrypt      = true
use_lockfile = true
