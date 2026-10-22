# Root Composition: one code base, selected per Environment with
# envs/<env>.tfvars and envs/<env>.backend.hcl (no workspaces).

locals {
  project = "franchise-inventory"

  # Every resource name is "<name_prefix>-<resource>", e.g. franchise-inventory-dev-api.
  name_prefix = "${local.project}-${var.environment}"

  # Full image reference for the ECS task definition. Null until an Image Tag is set.
  container_image = var.image_tag == null ? null : "${module.ecr.repository_url}:${var.image_tag}"
}

module "ecr" {
  source = "./modules/ecr"

  repository_name = "${local.name_prefix}-api"
  force_delete    = var.ecr_force_delete
  max_image_count = var.ecr_max_image_count
}

module "network" {
  source = "./modules/network"

  name_prefix        = local.name_prefix
  vpc_cidr           = var.vpc_cidr
  az_count           = var.az_count
  enable_nat_gateway = var.enable_nat_gateway
}

module "database" {
  source = "./modules/database"

  providers = {
    aws             = aws
    aws.db_password = aws.db_password
  }

  name_prefix = local.name_prefix
  vpc_id      = module.network.vpc_id
  subnet_ids  = module.network.private_subnet_ids

  # Empty until the ecs-service module exists. That module then passes the task
  # security group, so only the tasks can reach PostgreSQL.
  allowed_security_group_ids = []

  engine_version              = var.db_engine_version
  instance_class              = var.db_instance_class
  allocated_storage           = var.db_allocated_storage
  max_allocated_storage       = var.db_max_allocated_storage
  multi_az                    = var.db_multi_az
  backup_retention_days       = var.db_backup_retention_days
  deletion_protection         = var.db_deletion_protection
  skip_final_snapshot         = var.db_skip_final_snapshot
  secret_recovery_window_days = var.db_secret_recovery_window_days
  password_version            = var.db_password_version
}
