# prod: durable settings. Destroy never removes images by accident.
# Keep tests/prod.tftest.hcl in sync with these values.
environment = "prod"

# ECR: keep a longer history so older images stay available for rollback.
ecr_force_delete    = false
ecr_max_image_count = 30

# Network: one NAT Gateway, so the private subnets have internet egress.
vpc_cidr           = "10.30.0.0/16"
az_count           = 2
enable_nat_gateway = true
