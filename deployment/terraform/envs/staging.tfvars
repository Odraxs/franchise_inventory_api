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

# Database: the dev instance size (cheap), but it rehearses the prod safety settings
# that change how destroy behaves: storage autoscaling, a final snapshot and a
# secret recovery window. Single-AZ and no deletion protection, so a short-lived
# staging can still be destroyed without a manual step.
db_instance_class              = "db.t4g.micro"
db_allocated_storage           = 20
db_max_allocated_storage       = 50
db_multi_az                    = false
db_backup_retention_days       = 3
db_deletion_protection         = false
db_skip_final_snapshot         = false
db_secret_recovery_window_days = 7

# ALB: public HTTP on port 80. Set alb_certificate_arn to an ACM certificate ARN
# to add HTTPS on port 443 and redirect HTTP to it.
alb_allowed_ingress_cidrs = ["0.0.0.0/0"]

# Cost alarm: off. The budget protects the Free plan credits of the dev account only.
enable_budget = false
