# staging: production-like settings, smaller history.
# Keep tests/staging.tftest.hcl in sync with these values.
environment = "staging"

# ECR
ecr_force_delete    = false
ecr_max_image_count = 10

# Network: same layout as prod (one NAT Gateway, private subnets with egress), so
# staging rehearses the prod network. staging is not deployed for now. If it is
# deployed only for a short test, set enable_nat_gateway = false to save the NAT cost.
vpc_cidr           = "10.20.0.0/16"
az_count           = 2
enable_nat_gateway = true
