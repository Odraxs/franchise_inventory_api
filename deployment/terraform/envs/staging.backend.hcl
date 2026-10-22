# Partial S3 backend configuration for staging. The bucket is passed at init:
#   terraform init -backend-config=envs/staging.backend.hcl -backend-config="bucket=<state-bucket>"
key          = "franchise-inventory/staging/terraform.tfstate"
region       = "us-east-1"
encrypt      = true
use_lockfile = true
