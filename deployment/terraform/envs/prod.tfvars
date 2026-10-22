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

# Database: durable. Multi-AZ standby, storage autoscaling up to 100 GB, 7-day
# backups, deletion protection and a final snapshot. A deleted DB Secret can be
# restored for 30 days.
db_instance_class              = "db.t4g.small"
db_allocated_storage           = 20
db_max_allocated_storage       = 100
db_multi_az                    = true
db_backup_retention_days       = 7
db_deletion_protection         = true
db_skip_final_snapshot         = false
db_secret_recovery_window_days = 30

# Cost alarm: off. The budget protects the Free plan credits of the dev account only.
enable_budget = false
