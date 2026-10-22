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

# Database: smallest instance, Single-AZ, and easy to destroy and recreate.
# The DB Secret is deleted at once (window 0), so the same name can be created again.
db_instance_class              = "db.t4g.micro"
db_allocated_storage           = 20
db_max_allocated_storage       = null
db_multi_az                    = false
db_backup_retention_days       = 1
db_deletion_protection         = false
db_skip_final_snapshot         = true
db_secret_recovery_window_days = 0

# ALB: public HTTP on port 80. No domain, so no certificate (alb_certificate_arn stays empty).
alb_allowed_ingress_cidrs = ["0.0.0.0/0"]

# ECS: Fargate Spot (much cheaper; a reclaimed task is replaced), tasks in the
# public subnets with a public IP (no NAT Gateway), 1 task, logs kept 7 days.
tasks_in_private_subnets = false
ecs_capacity_provider    = "FARGATE_SPOT"
ecs_desired_count        = 1
ecs_log_retention_days   = 7

# Service Auto Scaling on average CPU (target 75 percent): 1 to 2 tasks. The
# max of 2 limits the cost of a load spike in dev.
autoscaling_min_capacity = 1
autoscaling_max_capacity = 2

# Cost alarm: USD 20/month AWS Budget with email alerts at 50, 80 and 100 percent.
# The email is never committed: export TF_VAR_budget_alert_email=<address> before plan/apply.
enable_budget = true
