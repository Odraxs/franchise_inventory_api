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

# API Secret: the API Credential (username and a random password) of the single
# trusted Client. Same recovery window as the DB Secret.
# Rotate it: increase api_credential_version by 1, commit, apply, then force a new
# ECS deployment (see README, "Rotate the API Credential").
api_secret_recovery_window_days = 7
api_credential_version          = 1

# ALB: public HTTP on port 80. Set alb_certificate_arn to an ACM certificate ARN
# to add HTTPS on port 443 and redirect HTTP to it.
alb_allowed_ingress_cidrs = ["0.0.0.0/0"]

# ECS: private tasks behind the NAT Gateway, like prod, so staging rehearses the
# prod network path. Fargate Spot and 1 task keep the cost down: a Spot reclaim
# in staging only means a short gap. Logs kept 14 days. If enable_nat_gateway is
# set to false for a short test, set tasks_in_private_subnets = false too.
tasks_in_private_subnets = true
ecs_capacity_provider    = "FARGATE_SPOT"
ecs_desired_count        = 1
ecs_log_retention_days   = 14

# Service Auto Scaling on average CPU (target 75 percent): 1 to 2 tasks, like
# dev. 2 tasks are enough to rehearse a scale-out; a higher max only adds cost.
autoscaling_min_capacity = 1
autoscaling_max_capacity = 2

# Cost alarm: off. The budget protects the Free plan credits of the dev account only.
enable_budget = false
