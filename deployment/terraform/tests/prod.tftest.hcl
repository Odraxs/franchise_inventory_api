# Plans the Root Composition with the prod Environment values. No AWS account needed.
# terraform test cannot load a -var-file per run, so the variables block below
# mirrors envs/prod.tfvars. Keep both files in sync.
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
  environment         = "prod"
  ecr_force_delete    = false
  ecr_max_image_count = 30
  vpc_cidr            = "10.30.0.0/16"
  az_count            = 2
  enable_nat_gateway  = true
  enable_budget       = false

  db_instance_class              = "db.t4g.small"
  db_allocated_storage           = 20
  db_max_allocated_storage       = 100
  db_multi_az                    = true
  db_backup_retention_days       = 7
  db_deletion_protection         = true
  db_skip_final_snapshot         = false
  db_secret_recovery_window_days = 30
}

run "prod_plan" {
  command = plan

  assert {
    condition     = output.ecr_repository_name == "franchise-inventory-prod-api"
    error_message = "The prod ECR repository must be named franchise-inventory-prod-api."
  }

  assert {
    condition     = module.ecr.image_tag_mutability == "IMMUTABLE"
    error_message = "The prod ECR repository must have IMMUTABLE tags."
  }

  assert {
    condition     = output.container_image == null
    error_message = "Without image_tag, no container image may be selected."
  }

  assert {
    condition     = length(module.network.nat_gateway_ids) == 1 && module.network.nat_eip_count == 1
    error_message = "prod must plan one NAT Gateway with one Elastic IP."
  }

  assert {
    condition     = length(module.network.public_subnet_ids) == 2 && length(distinct(module.network.public_subnet_availability_zones)) == 2
    error_message = "prod must plan one public subnet in each of 2 AZs."
  }

  assert {
    condition     = length(module.network.private_subnet_ids) == 2 && length(distinct(module.network.private_subnet_availability_zones)) == 2
    error_message = "prod must plan one private subnet in each of 2 AZs."
  }

  assert {
    condition     = length(aws_budgets_budget.monthly_cost) == 0
    error_message = "prod must not plan the cost budget."
  }

  assert {
    condition     = output.db_secret_name == "franchise-inventory-prod-db"
    error_message = "The prod DB Secret must be named franchise-inventory-prod-db."
  }

  assert {
    condition = (
      module.database.instance_settings.engine == "postgres" &&
      startswith(module.database.instance_settings.engine_version, "17")
    )
    error_message = "The prod database must be PostgreSQL 17."
  }

  assert {
    condition = (
      module.database.instance_settings.instance_class == "db.t4g.small" &&
      module.database.instance_settings.allocated_storage == 20 &&
      module.database.instance_settings.max_allocated_storage == 100 &&
      module.database.instance_settings.multi_az == true
    )
    error_message = "prod must plan a Multi-AZ db.t4g.small with storage autoscaling from 20 to 100 GB."
  }

  assert {
    condition = (
      module.database.instance_settings.storage_type == "gp3" &&
      module.database.instance_settings.storage_encrypted == true &&
      module.database.instance_settings.publicly_accessible == false
    )
    error_message = "The prod database must use encrypted gp3 storage and must not be publicly accessible."
  }

  assert {
    condition = (
      module.database.instance_settings.backup_retention_period == 7 &&
      module.database.instance_settings.deletion_protection == true &&
      module.database.instance_settings.skip_final_snapshot == false &&
      module.database.instance_settings.final_snapshot_identifier == "franchise-inventory-prod-db-final"
    )
    error_message = "prod must keep 7 days of backups, with deletion protection and a final snapshot."
  }

  assert {
    condition     = module.database.instance_settings.secret_recovery_window_days == 30
    error_message = "The prod DB Secret must have a recovery window of 30 days."
  }

  assert {
    condition     = module.database.stores_plaintext_password == false
    error_message = "The DB password must only be written through write-only arguments."
  }
}
