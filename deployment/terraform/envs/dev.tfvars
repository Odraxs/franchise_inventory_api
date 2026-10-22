# dev: disposable and cheap. The only Environment that is deployed for now.
# Keep tests/dev.tftest.hcl in sync with these values.
environment = "dev"

# ECR
ecr_force_delete    = true
ecr_max_image_count = 10

# Network: no NAT Gateway (billed per hour). Tasks run in public subnets with a public IP.
vpc_cidr           = "10.10.0.0/16"
az_count           = 2
enable_nat_gateway = false

# Cost alarm: USD 20/month AWS Budget with email alerts at 50, 80 and 100 percent.
# The email is never committed: export TF_VAR_budget_alert_email=<address> before plan/apply.
enable_budget = true
