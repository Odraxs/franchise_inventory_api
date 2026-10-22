# Plans the Root Composition with the dev Environment values. No AWS account needed.
# terraform test cannot load a -var-file per run, so the variables block below
# mirrors envs/dev.tfvars. Keep both files in sync.
mock_provider "aws" {
  # The mocked data source returns no AZ names by default. Give it three, so the
  # network module must pick the first az_count of them.
  mock_data "aws_availability_zones" {
    defaults = {
      names = ["us-east-1a", "us-east-1b", "us-east-1c"]
    }
  }
}

# Mock providers cannot hold ephemeral resources, so the DB password provider is a
# real configuration that cannot reach AWS (fake keys, a closed local endpoint).
# override_module replaces the password module, so the provider is never called.
provider "aws" {
  alias                       = "db_password"
  region                      = "us-east-1"
  access_key                  = "mock"
  secret_key                  = "mock"
  skip_credentials_validation = true
  skip_requesting_account_id  = true
  skip_metadata_api_check     = true

  endpoints {
    secretsmanager = "http://127.0.0.1:9"
  }
}

override_module {
  target = module.database.module.password
  outputs = {
    value = "test-only-password"
  }
}

variables {
  environment         = "dev"
  ecr_force_delete    = true
  ecr_max_image_count = 10
  vpc_cidr            = "10.10.0.0/16"
  az_count            = 2
  enable_nat_gateway  = false
  enable_budget       = true

  db_instance_class              = "db.t4g.micro"
  db_allocated_storage           = 20
  db_max_allocated_storage       = null
  db_multi_az                    = false
  db_backup_retention_days       = 1
  db_deletion_protection         = false
  db_skip_final_snapshot         = true
  db_secret_recovery_window_days = 0

  alb_allowed_ingress_cidrs = ["0.0.0.0/0"]

  # Not in envs/dev.tfvars: the real address comes from TF_VAR_budget_alert_email.
  budget_alert_email = "test@example.com"
}

run "dev_plan" {
  command = plan

  assert {
    condition     = output.ecr_repository_name == "franchise-inventory-dev-api"
    error_message = "The dev ECR repository must be named franchise-inventory-dev-api."
  }

  assert {
    condition     = module.ecr.image_tag_mutability == "IMMUTABLE"
    error_message = "The dev ECR repository must have IMMUTABLE tags."
  }

  assert {
    condition     = output.container_image == null
    error_message = "Without image_tag, no container image may be selected."
  }

  assert {
    condition     = length(module.network.nat_gateway_ids) == 0 && module.network.nat_eip_count == 0
    error_message = "dev must plan no NAT Gateway and no Elastic IP."
  }

  assert {
    condition     = length(module.network.public_subnet_ids) == 2 && length(distinct(module.network.public_subnet_availability_zones)) == 2
    error_message = "dev must plan one public subnet in each of 2 AZs."
  }

  assert {
    condition     = length(module.network.private_subnet_ids) == 2 && length(distinct(module.network.private_subnet_availability_zones)) == 2
    error_message = "dev must plan one private subnet in each of 2 AZs."
  }

  assert {
    condition     = length(aws_budgets_budget.monthly_cost) == 1
    error_message = "dev must plan the monthly cost budget."
  }

  assert {
    condition = (
      aws_budgets_budget.monthly_cost[0].budget_type == "COST" &&
      tonumber(aws_budgets_budget.monthly_cost[0].limit_amount) == 20 &&
      aws_budgets_budget.monthly_cost[0].limit_unit == "USD" &&
      aws_budgets_budget.monthly_cost[0].time_unit == "MONTHLY"
    )
    error_message = "The dev budget must be a COST budget of USD 20 per month."
  }

  assert {
    condition     = toset([for n in aws_budgets_budget.monthly_cost[0].notification : n.threshold]) == toset([50, 80, 100])
    error_message = "The dev budget must notify at exactly 50, 80 and 100 percent."
  }

  assert {
    condition = alltrue([
      for n in aws_budgets_budget.monthly_cost[0].notification :
      n.threshold_type == "PERCENTAGE" && n.notification_type == "ACTUAL" && n.comparison_operator == "GREATER_THAN"
    ])
    error_message = "Each dev budget notification must fire when ACTUAL spend is GREATER_THAN a PERCENTAGE of the limit."
  }

  assert {
    condition = alltrue([
      for n in aws_budgets_budget.monthly_cost[0].notification :
      nonsensitive(n.subscriber_email_addresses) == toset(["test@example.com"])
    ])
    error_message = "Each dev budget notification must email budget_alert_email."
  }

  assert {
    condition     = output.db_secret_name == "franchise-inventory-dev-db"
    error_message = "The dev DB Secret must be named franchise-inventory-dev-db."
  }

  assert {
    condition = (
      module.database.instance_settings.engine == "postgres" &&
      startswith(module.database.instance_settings.engine_version, "17")
    )
    error_message = "The dev database must be PostgreSQL 17."
  }

  assert {
    condition = (
      module.database.instance_settings.instance_class == "db.t4g.micro" &&
      module.database.instance_settings.allocated_storage == 20 &&
      module.database.instance_settings.max_allocated_storage == null &&
      module.database.instance_settings.multi_az == false
    )
    error_message = "dev must plan a Single-AZ db.t4g.micro with 20 GB and no storage autoscaling."
  }

  assert {
    condition = (
      module.database.instance_settings.storage_type == "gp3" &&
      module.database.instance_settings.storage_encrypted == true &&
      module.database.instance_settings.publicly_accessible == false
    )
    error_message = "The dev database must use encrypted gp3 storage and must not be publicly accessible."
  }

  assert {
    condition = (
      module.database.instance_settings.backup_retention_period == 1 &&
      module.database.instance_settings.deletion_protection == false &&
      module.database.instance_settings.skip_final_snapshot == true &&
      module.database.instance_settings.final_snapshot_identifier == null
    )
    error_message = "dev must keep 1 day of backups, with no deletion protection and no final snapshot."
  }

  assert {
    condition     = module.database.instance_settings.secret_recovery_window_days == 0
    error_message = "The dev DB Secret must have a recovery window of 0 days."
  }

  assert {
    condition     = module.database.stores_plaintext_password == false
    error_message = "The DB password must only be written through write-only arguments."
  }

  assert {
    condition     = length(module.database.postgres_ingress_rules) == 0
    error_message = "Until the ECS tasks exist, no client may reach the dev database."
  }

  assert {
    condition = (
      module.alb.actuator_rule.listener_port == 80 &&
      module.alb.actuator_rule.action_type == "fixed-response" &&
      module.alb.actuator_rule.status_code == "404" &&
      contains(module.alb.actuator_rule.path_patterns, "/actuator/*") &&
      contains(module.alb.actuator_rule.path_patterns, "/actuator")
    )
    error_message = "The dev ALB must answer a fixed 404 for /actuator and /actuator/* on the HTTP listener."
  }

  assert {
    condition = (
      module.alb.health_check.path == "/actuator/health/readiness" &&
      module.alb.health_check.matcher == "200" &&
      module.alb.health_check.port == "traffic-port"
    )
    error_message = "The dev target group must expect 200 from /actuator/health/readiness on the container port."
  }

  assert {
    condition = (
      module.alb.target_group_settings.target_type == "ip" &&
      module.alb.target_group_settings.port == 8080
    )
    error_message = "The dev target group must have target type ip on port 8080."
  }

  assert {
    condition     = module.alb.ingress_rules == toset([{ cidr = "0.0.0.0/0", port = 80 }])
    error_message = "Without a certificate, the dev ALB must accept only port 80, from alb_allowed_ingress_cidrs."
  }

  assert {
    condition     = module.alb.listeners == [{ port = 80, protocol = "HTTP", default_action = "forward" }]
    error_message = "Without a certificate, the dev ALB must have only an HTTP listener that forwards to the tasks."
  }
}
