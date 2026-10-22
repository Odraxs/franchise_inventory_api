# Plans the Root Composition with the staging Environment values. No AWS account needed.
# terraform test cannot load a -var-file per run, so the variables block below
# mirrors envs/staging.tfvars. Keep both files in sync.
mock_provider "aws" {
  # Give the computed values (IDs, ARNs) known values during plan, so the tests
  # can compare the IAM policies and the security group references.
  override_during = plan

  # The mocked data source returns no AZ names by default. Give it three, so the
  # network module must pick the first az_count of them.
  mock_data "aws_availability_zones" {
    defaults = {
      names = ["us-east-1a", "us-east-1b", "us-east-1c"]
    }
  }

  mock_data "aws_caller_identity" {
    defaults = {
      account_id = "123456789012"
    }
  }

  # Known ARNs, so the IAM policy scope can be compared exactly.
  mock_resource "aws_ecr_repository" {
    defaults = {
      arn            = "arn:aws:ecr:us-east-1:123456789012:repository/franchise-inventory-staging-api"
      repository_url = "123456789012.dkr.ecr.us-east-1.amazonaws.com/franchise-inventory-staging-api"
    }
  }

  mock_resource "aws_cloudwatch_log_group" {
    defaults = {
      arn = "arn:aws:logs:us-east-1:123456789012:log-group:/ecs/franchise-inventory-staging-api"
    }
  }

  mock_resource "aws_secretsmanager_secret" {
    defaults = {
      arn = "arn:aws:secretsmanager:us-east-1:123456789012:secret:franchise-inventory-staging-db-AbCdEf"
    }
  }

  # The provider validates the ARN format of these arguments, so they need
  # well-formed ARNs instead of random strings.
  mock_resource "aws_iam_role" {
    defaults = {
      arn = "arn:aws:iam::123456789012:role/franchise-inventory-staging-ecs"
    }
  }

  mock_resource "aws_lb" {
    defaults = {
      arn = "arn:aws:elasticloadbalancing:us-east-1:123456789012:loadbalancer/app/franchise-inventory-staging-alb/0123456789abcdef"
    }
  }

  mock_resource "aws_lb_target_group" {
    defaults = {
      arn = "arn:aws:elasticloadbalancing:us-east-1:123456789012:targetgroup/franchise-inventory-staging-api/0123456789abcdef"
    }
  }

  mock_resource "aws_lb_listener" {
    defaults = {
      arn = "arn:aws:elasticloadbalancing:us-east-1:123456789012:listener/app/franchise-inventory-staging-alb/0123456789abcdef/0123456789abcdef"
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

# Distinct, known security group IDs, so the tests can check which group
# references which (ALB -> tasks -> database).
override_resource {
  target          = module.alb.aws_security_group.alb
  override_during = plan
  values = {
    id = "sg-0a1b0000000000a1b"
  }
}

override_resource {
  target          = module.ecs_service.aws_security_group.task
  override_during = plan
  values = {
    id = "sg-0ta5k000000000ta5"
  }
}

override_resource {
  target          = module.database.aws_security_group.db
  override_during = plan
  values = {
    id = "sg-0db00000000000db0"
  }
}

# Known subnet IDs, so the tests can check the task placement. Every instance
# of a counted resource gets the same override, so one ID stands for each tier.
override_resource {
  target          = module.network.aws_subnet.public
  override_during = plan
  values = {
    id = "subnet-0pub000000000pub"
  }
}

override_resource {
  target          = module.network.aws_subnet.private
  override_during = plan
  values = {
    id = "subnet-0prv000000000prv"
  }
}

variables {
  environment         = "staging"
  ecr_force_delete    = false
  ecr_max_image_count = 10
  vpc_cidr            = "10.20.0.0/16"
  az_count            = 2
  enable_nat_gateway  = true
  enable_budget       = false

  db_instance_class              = "db.t4g.micro"
  db_allocated_storage           = 20
  db_max_allocated_storage       = 50
  db_multi_az                    = false
  db_backup_retention_days       = 3
  db_deletion_protection         = false
  db_skip_final_snapshot         = false
  db_secret_recovery_window_days = 7

  alb_allowed_ingress_cidrs = ["0.0.0.0/0"]

  tasks_in_private_subnets = true
  ecs_capacity_provider    = "FARGATE_SPOT"
  ecs_desired_count        = 1
  ecs_log_retention_days   = 14

  autoscaling_min_capacity = 1
  autoscaling_max_capacity = 2
}

run "staging_plan" {
  command = plan

  assert {
    condition     = output.ecr_repository_name == "franchise-inventory-staging-api"
    error_message = "The staging ECR repository must be named franchise-inventory-staging-api."
  }

  assert {
    condition     = module.ecr.image_tag_mutability == "IMMUTABLE"
    error_message = "The staging ECR repository must have IMMUTABLE tags."
  }

  assert {
    condition     = output.container_image == null
    error_message = "Without image_tag, no container image may be selected."
  }

  assert {
    condition     = length(module.network.nat_gateway_ids) == 1 && module.network.nat_eip_count == 1
    error_message = "staging must plan one NAT Gateway with one Elastic IP, like prod."
  }

  assert {
    condition     = length(module.network.public_subnet_ids) == 2 && length(distinct(module.network.public_subnet_availability_zones)) == 2
    error_message = "staging must plan one public subnet in each of 2 AZs."
  }

  assert {
    condition     = length(module.network.private_subnet_ids) == 2 && length(distinct(module.network.private_subnet_availability_zones)) == 2
    error_message = "staging must plan one private subnet in each of 2 AZs."
  }

  assert {
    condition     = length(aws_budgets_budget.monthly_cost) == 0
    error_message = "staging must not plan the cost budget."
  }

  assert {
    condition     = output.db_secret_name == "franchise-inventory-staging-db"
    error_message = "The staging DB Secret must be named franchise-inventory-staging-db."
  }

  assert {
    condition = (
      module.database.instance_settings.engine == "postgres" &&
      startswith(module.database.instance_settings.engine_version, "17")
    )
    error_message = "The staging database must be PostgreSQL 17."
  }

  assert {
    condition = (
      module.database.instance_settings.instance_class == "db.t4g.micro" &&
      module.database.instance_settings.allocated_storage == 20 &&
      module.database.instance_settings.max_allocated_storage == 50 &&
      module.database.instance_settings.multi_az == false
    )
    error_message = "staging must plan a Single-AZ db.t4g.micro with storage autoscaling from 20 to 50 GB."
  }

  assert {
    condition = (
      module.database.instance_settings.storage_type == "gp3" &&
      module.database.instance_settings.storage_encrypted == true &&
      module.database.instance_settings.publicly_accessible == false
    )
    error_message = "The staging database must use encrypted gp3 storage and must not be publicly accessible."
  }

  assert {
    condition = (
      module.database.instance_settings.backup_retention_period == 3 &&
      module.database.instance_settings.deletion_protection == false &&
      module.database.instance_settings.skip_final_snapshot == false &&
      module.database.instance_settings.final_snapshot_identifier == "franchise-inventory-staging-db-final"
    )
    error_message = "staging must keep 3 days of backups and take a final snapshot, without deletion protection."
  }

  assert {
    condition     = module.database.instance_settings.secret_recovery_window_days == 7
    error_message = "The staging DB Secret must have a recovery window of 7 days."
  }

  assert {
    condition     = module.database.stores_plaintext_password == false
    error_message = "The DB password must only be written through write-only arguments."
  }

  assert {
    condition = (
      module.alb.actuator_rule.status_code == "404" &&
      contains(module.alb.actuator_rule.path_patterns, "/actuator/*")
    )
    error_message = "The staging ALB must answer a fixed 404 for /actuator/*."
  }

  assert {
    condition     = module.alb.health_check.path == "/actuator/health/readiness"
    error_message = "The staging target group must check /actuator/health/readiness."
  }

  assert {
    condition     = module.alb.ingress_rules == toset([{ cidr = "0.0.0.0/0", port = 80 }])
    error_message = "The staging ALB must accept only port 80, from alb_allowed_ingress_cidrs."
  }

  assert {
    condition = module.database.postgres_ingress_rules == [{
      from_port                = 5432
      to_port                  = 5432
      source_security_group_id = module.ecs_service.task_security_group_id
    }]
    error_message = "The staging database must accept PostgreSQL (5432) only from the task security group."
  }

  assert {
    condition = (
      module.ecs_service.task_definition == null &&
      module.ecs_service.service_settings == null &&
      output.ecs_service_name == null
    )
    error_message = "Without image_tag, no task definition and no ECS service may be planned."
  }

  assert {
    condition     = module.ecs_service.autoscaling == null
    error_message = "Without image_tag, no scaling target and no scaling policy may be planned."
  }

  assert {
    condition     = module.ecs_service.log_retention_days == 14
    error_message = "staging must keep the container logs for 14 days."
  }

  assert {
    condition = module.ecs_service.task_role_policy.Statement == [{
      Sid      = "ReadDbSecret"
      Effect   = "Allow"
      Action   = ["secretsmanager:GetSecretValue"]
      Resource = "arn:aws:secretsmanager:us-east-1:123456789012:secret:franchise-inventory-staging-db-AbCdEf"
    }]
    error_message = "The staging task role may only call secretsmanager:GetSecretValue on the DB Secret ARN."
  }
}

run "staging_service_plan" {
  command = plan

  variables {
    image_tag = "0123456789ab"
  }

  assert {
    condition     = output.ecs_service_name == "franchise-inventory-staging-api"
    error_message = "With image_tag, the staging ECS service must be planned."
  }

  assert {
    condition = (
      module.ecs_service.service_settings.capacity_providers == ["FARGATE_SPOT"] &&
      module.ecs_service.service_settings.desired_count == 1
    )
    error_message = "staging must run 1 task(s) on FARGATE_SPOT."
  }

  assert {
    condition = (
      toset(module.ecs_service.service_settings.subnets) == toset(module.network.private_subnet_ids) &&
      module.ecs_service.service_settings.assign_public_ip == false
    )
    error_message = "staging tasks must run in the private subnets without a public IP."
  }

  assert {
    condition = (
      module.ecs_service.service_settings.health_check_grace_period_seconds == 120 &&
      module.ecs_service.service_settings.deployment_minimum_healthy_percent == 100 &&
      module.ecs_service.service_settings.deployment_maximum_percent == 200 &&
      module.ecs_service.service_settings.circuit_breaker_enable == true &&
      module.ecs_service.service_settings.circuit_breaker_rollback == true &&
      module.ecs_service.service_settings.enable_execute_command == false
    )
    error_message = "The staging service must use a 120 s grace period, 100/200 percent, a circuit breaker with rollback and no ECS Exec."
  }

  assert {
    condition = toset(module.ecs_service.task_definition.containers[0].environment) == toset([
      { name = "SPRING_PROFILES_ACTIVE", value = "aws" },
      { name = "DB_SECRET_NAME", value = "franchise-inventory-staging-db" },
      { name = "AWS_REGION", value = "us-east-1" },
    ]) && !contains(keys(module.ecs_service.task_definition.containers[0]), "secrets")
    error_message = "The staging container must get only the three non-secret variables and no secrets block."
  }

  assert {
    condition = (
      module.ecs_service.autoscaling.service_namespace == "ecs" &&
      module.ecs_service.autoscaling.resource_id == "service/franchise-inventory-staging/franchise-inventory-staging-api" &&
      module.ecs_service.autoscaling.scalable_dimension == "ecs:service:DesiredCount"
    )
    error_message = "The staging scaling target must be the desired count of service franchise-inventory-staging-api."
  }

  assert {
    condition = (
      module.ecs_service.autoscaling.min_capacity == 1 &&
      module.ecs_service.autoscaling.max_capacity == 2
    )
    error_message = "staging must scale between 1 and 2 tasks."
  }

  assert {
    condition = (
      module.ecs_service.autoscaling.policy_type == "TargetTrackingScaling" &&
      module.ecs_service.autoscaling.predefined_metric_type == "ECSServiceAverageCPUUtilization" &&
      module.ecs_service.autoscaling.target_value == 75
    )
    error_message = "The staging scaling policy must track the average service CPU at a target of 75 percent."
  }

  assert {
    condition = (
      module.ecs_service.autoscaling.scale_out_cooldown == 60 &&
      module.ecs_service.autoscaling.scale_in_cooldown == 300 &&
      module.ecs_service.autoscaling.disable_scale_in == false
    )
    error_message = "The staging scaling policy must scale out after 60 s, scale in after 300 s and keep scale-in on."
  }
}
