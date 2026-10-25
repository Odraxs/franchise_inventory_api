# Root Composition: one code base, selected per Environment with
# envs/<env>.tfvars and envs/<env>.backend.hcl (no workspaces).

data "aws_caller_identity" "current" {}

locals {
  project = "franchise-inventory"

  # Created by the Bootstrap Stack (bootstrap/deployer.tf) under this fixed name.
  role_permissions_boundary_arn = "arn:aws:iam::${data.aws_caller_identity.current.account_id}:policy/${local.project}-ecs-role-boundary"

  # Every resource name is "<name_prefix>-<resource>", e.g. franchise-inventory-dev-api.
  name_prefix = "${local.project}-${var.environment}"

  # Full image reference for the ECS task definition. Null until an Image Tag is set.
  container_image = var.image_tag == null ? null : "${module.ecr.repository_url}:${var.image_tag}"

  # dev: public subnets with a public IP (no NAT Gateway). prod: private subnets
  # without a public IP, behind the NAT Gateway (see var.tasks_in_private_subnets).
  task_subnet_ids = var.tasks_in_private_subnets ? module.network.private_subnet_ids : module.network.public_subnet_ids

  # With a certificate, the ALB serves HTTPS and redirects HTTP to it.
  alb_url = "${var.alb_certificate_arn == "" ? "http" : "https"}://${module.alb.dns_name}"
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
    aws           = aws
    aws.ephemeral = aws.ephemeral
  }

  name_prefix = local.name_prefix
  vpc_id      = module.network.vpc_id
  subnet_ids  = module.network.private_subnet_ids

  # Only the ECS tasks can reach PostgreSQL.
  allowed_security_group_ids = [module.ecs_service.task_security_group_id]

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

module "api_secret" {
  source = "./modules/api-secret"

  providers = {
    aws           = aws
    aws.ephemeral = aws.ephemeral
  }

  name_prefix          = local.name_prefix
  client_username      = var.api_client_username
  recovery_window_days = var.api_secret_recovery_window_days
  credential_version   = var.api_credential_version
}

module "alb" {
  source = "./modules/alb"

  name_prefix           = local.name_prefix
  vpc_id                = module.network.vpc_id
  vpc_cidr              = var.vpc_cidr
  public_subnet_ids     = module.network.public_subnet_ids
  container_port        = var.container_port
  allowed_ingress_cidrs = var.alb_allowed_ingress_cidrs
  certificate_arn       = var.alb_certificate_arn
}

# The first apply runs without image_tag: container_image is null, so the module
# creates no task definition and no service. See modules/ecs-service/main.tf.
module "ecs_service" {
  source = "./modules/ecs-service"

  name_prefix        = local.name_prefix
  aws_region         = var.aws_region
  container_image    = local.container_image
  ecr_repository_arn = module.ecr.repository_arn

  role_permissions_boundary_arn = local.role_permissions_boundary_arn

  vpc_id                = module.network.vpc_id
  subnet_ids            = local.task_subnet_ids
  assign_public_ip      = !var.tasks_in_private_subnets
  alb_security_group_id = module.alb.security_group_id
  target_group_arn      = module.alb.target_group_arn

  db_security_group_id = module.database.security_group_id
  db_port              = module.database.port
  db_secret_arn        = module.database.secret_arn
  db_secret_name       = module.database.secret_name
  api_secret_arn       = module.api_secret.secret_arn
  api_secret_name      = module.api_secret.secret_name

  container_port     = var.container_port
  capacity_provider  = var.ecs_capacity_provider
  desired_count      = var.ecs_desired_count
  log_retention_days = var.ecs_log_retention_days

  # Scale out quickly (60 s) and scale in slowly (300 s): module defaults.
  autoscaling_min_capacity = var.autoscaling_min_capacity
  autoscaling_max_capacity = var.autoscaling_max_capacity
  autoscaling_cpu_target   = var.autoscaling_cpu_target
}
