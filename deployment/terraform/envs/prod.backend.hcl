# Partial S3 backend configuration for prod. The bucket is passed at init:
#   terraform init -backend-config=envs/prod.backend.hcl -backend-config="bucket=<state-bucket>"
key          = "franchise-inventory/prod/terraform.tfstate"
region       = "us-east-1"
encrypt      = true
use_lockfile = true
