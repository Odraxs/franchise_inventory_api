# Plans the Root Composition with the dev Environment values. No AWS account needed.
# terraform test cannot load a -var-file per run, so the variables block below
# mirrors envs/dev.tfvars. Keep both files in sync.
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
      arn            = "arn:aws:ecr:us-east-1:123456789012:repository/franchise-inventory-dev-api"
      repository_url = "123456789012.dkr.ecr.us-east-1.amazonaws.com/franchise-inventory-dev-api"
    }
  }

  mock_resource "aws_cloudwatch_log_group" {
    defaults = {
      arn = "arn:aws:logs:us-east-1:123456789012:log-group:/ecs/franchise-inventory-dev-api"
    }
  }

  mock_resource "aws_secretsmanager_secret" {
    defaults = {
      arn = "arn:aws:secretsmanager:us-east-1:123456789012:secret:franchise-inventory-dev-db-AbCdEf"
    }
  }

  # The provider validates the ARN format of these arguments, so they need
  # well-formed ARNs instead of random strings.
  mock_resource "aws_iam_role" {
    defaults = {
      arn = "arn:aws:iam::123456789012:role/franchise-inventory-dev-ecs"
    }
  }

  mock_resource "aws_lb" {
    defaults = {
      arn = "arn:aws:elasticloadbalancing:us-east-1:123456789012:loadbalancer/app/franchise-inventory-dev-alb/0123456789abcdef"
    }
  }

  mock_resource "aws_lb_target_group" {
    defaults = {
      arn = "arn:aws:elasticloadbalancing:us-east-1:123456789012:targetgroup/franchise-inventory-dev-api/0123456789abcdef"
    }
  }

  mock_resource "aws_lb_listener" {
    defaults = {
      arn = "arn:aws:elasticloadbalancing:us-east-1:123456789012:listener/app/franchise-inventory-dev-alb/0123456789abcdef/0123456789abcdef"
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

  tasks_in_private_subnets = false
  ecs_capacity_provider    = "FARGATE_SPOT"
  ecs_desired_count        = 1
  ecs_log_retention_days   = 7

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
    condition = module.database.postgres_ingress_rules == [{
      from_port                = 5432
      to_port                  = 5432
      source_security_group_id = module.ecs_service.task_security_group_id
    }]
    error_message = "The dev database must accept PostgreSQL (5432) only from the task security group."
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

  # ECS: without image_tag (first apply), nothing that needs the image is planned.
  assert {
    condition = (
      module.ecs_service.task_definition == null &&
      module.ecs_service.service_settings == null &&
      output.ecs_service_name == null
    )
    error_message = "Without image_tag, no task definition and no ECS service may be planned."
  }

  assert {
    condition = (
      output.ecs_cluster_name == "franchise-inventory-dev" &&
      module.ecs_service.cluster_settings.container_insights == "disabled" &&
      toset(module.ecs_service.cluster_settings.capacity_providers) == toset(["FARGATE", "FARGATE_SPOT"])
    )
    error_message = "The dev cluster must offer FARGATE and FARGATE_SPOT, with Container Insights off."
  }

  assert {
    condition = (
      output.ecs_log_group_name == "/ecs/franchise-inventory-dev-api" &&
      module.ecs_service.log_retention_days == 7
    )
    error_message = "dev must keep the container logs in /ecs/franchise-inventory-dev-api for 7 days."
  }

  assert {
    condition = module.ecs_service.execution_role_policy == {
      Version = "2012-10-17"
      Statement = [
        {
          Sid      = "EcrAuthToken"
          Effect   = "Allow"
          Action   = ["ecr:GetAuthorizationToken"]
          Resource = "*"
        },
        {
          Sid      = "EcrPullEnvironmentRepository"
          Effect   = "Allow"
          Action   = ["ecr:BatchCheckLayerAvailability", "ecr:BatchGetImage", "ecr:GetDownloadUrlForLayer"]
          Resource = "arn:aws:ecr:us-east-1:123456789012:repository/franchise-inventory-dev-api"
        },
        {
          Sid      = "WriteServiceLogs"
          Effect   = "Allow"
          Action   = ["logs:CreateLogStream", "logs:PutLogEvents"]
          Resource = "arn:aws:logs:us-east-1:123456789012:log-group:/ecs/franchise-inventory-dev-api:*"
        },
      ]
    }
    error_message = "The execution role may only get the ECR token, pull from the dev repository and write to the service log group."
  }

  assert {
    condition = module.ecs_service.task_role_policy == {
      Version = "2012-10-17"
      Statement = [
        {
          Sid      = "ReadDbSecret"
          Effect   = "Allow"
          Action   = ["secretsmanager:GetSecretValue"]
          Resource = "arn:aws:secretsmanager:us-east-1:123456789012:secret:franchise-inventory-dev-db-AbCdEf"
        },
      ]
    }
    error_message = "The task role may only call secretsmanager:GetSecretValue on the DB Secret ARN."
  }

  assert {
    condition = alltrue([
      for policy in values(module.ecs_service.assume_role_policies) :
      length(policy.Statement) == 1 &&
      policy.Statement[0].Principal == { Service = "ecs-tasks.amazonaws.com" } &&
      policy.Statement[0].Action == "sts:AssumeRole" &&
      policy.Statement[0].Condition == { StringEquals = { "aws:SourceAccount" = "123456789012" } }
    ])
    error_message = "Both ECS roles may be assumed only by ecs-tasks.amazonaws.com of the same account."
  }

  assert {
    condition = (
      module.ecs_service.task_security_group_rules.ingress == {
        from_port                = 8080
        to_port                  = 8080
        source_security_group_id = module.alb.security_group_id
      }
    )
    error_message = "The task security group must accept only port 8080, only from the ALB security group."
  }

  assert {
    condition = toset(module.ecs_service.task_security_group_rules.egress) == toset([
      { port = 443, cidr_ipv4 = "0.0.0.0/0", destination_security_group_id = null },
      { port = 5432, cidr_ipv4 = null, destination_security_group_id = module.database.security_group_id },
    ])
    error_message = "The tasks may only open HTTPS (AWS endpoints) and PostgreSQL to the database security group."
  }
}

run "dev_service_plan" {
  command = plan

  variables {
    image_tag = "0123456789ab"
  }

  assert {
    condition     = output.ecs_service_name == "franchise-inventory-dev-api"
    error_message = "With image_tag, the dev ECS service must be planned."
  }

  assert {
    condition = (
      module.ecs_service.task_definition.cpu == "512" &&
      module.ecs_service.task_definition.memory == "1024" &&
      module.ecs_service.task_definition.network_mode == "awsvpc" &&
      toset(module.ecs_service.task_definition.requires_compatibilities) == toset(["FARGATE"]) &&
      module.ecs_service.task_definition.operating_system_family == "LINUX" &&
      module.ecs_service.task_definition.cpu_architecture == "X86_64"
    )
    error_message = "The dev task definition must be Fargate, awsvpc, 0.5 vCPU, 1 GB, LINUX/X86_64."
  }

  assert {
    condition = (
      length(module.ecs_service.task_definition.containers) == 1 &&
      module.ecs_service.task_definition.containers[0].image == "123456789012.dkr.ecr.us-east-1.amazonaws.com/franchise-inventory-dev-api:0123456789ab" &&
      module.ecs_service.task_definition.containers[0].portMappings[0].containerPort == 8080
    )
    error_message = "The dev container must run <ecr url>:<image_tag> and listen on port 8080."
  }

  assert {
    condition = (
      module.ecs_service.task_definition.containers[0].logConfiguration.logDriver == "awslogs" &&
      module.ecs_service.task_definition.containers[0].logConfiguration.options["awslogs-group"] == "/ecs/franchise-inventory-dev-api" &&
      module.ecs_service.task_definition.containers[0].logConfiguration.options["awslogs-region"] == "us-east-1"
    )
    error_message = "The dev container must send its logs with awslogs to the service log group."
  }

  # The container gets only the three non-secret variables. The app reads the
  # credentials itself from the DB Secret.
  assert {
    condition = toset(module.ecs_service.task_definition.containers[0].environment) == toset([
      { name = "SPRING_PROFILES_ACTIVE", value = "aws" },
      { name = "DB_SECRET_NAME", value = "franchise-inventory-dev-db" },
      { name = "AWS_REGION", value = "us-east-1" },
    ])
    error_message = "The container environment must hold only SPRING_PROFILES_ACTIVE=aws, DB_SECRET_NAME and AWS_REGION."
  }

  assert {
    condition = alltrue([
      for c in module.ecs_service.task_definition.containers :
      !contains(keys(c), "secrets") &&
      alltrue([for e in c.environment : !can(regex("(?i)password", e.name)) && !strcontains(e.value, "test-only-password")])
    ])
    error_message = "No container may have a secrets block or an environment variable that holds a credential."
  }

  assert {
    condition = (
      module.ecs_service.service_settings.capacity_providers == ["FARGATE_SPOT"] &&
      module.ecs_service.service_settings.desired_count == 1
    )
    error_message = "dev must run 1 task on FARGATE_SPOT."
  }

  assert {
    condition = (
      toset(module.ecs_service.service_settings.subnets) == toset(module.network.public_subnet_ids) &&
      module.ecs_service.service_settings.assign_public_ip == true &&
      toset(module.ecs_service.service_settings.security_groups) == toset([module.ecs_service.task_security_group_id])
    )
    error_message = "dev tasks must run in the public subnets with a public IP, in the task security group only."
  }

  assert {
    condition = (
      module.ecs_service.service_settings.target_group_arn == "arn:aws:elasticloadbalancing:us-east-1:123456789012:targetgroup/franchise-inventory-dev-api/0123456789abcdef" &&
      module.ecs_service.service_settings.container_name == "api" &&
      module.ecs_service.service_settings.container_port == 8080
    )
    error_message = "The dev service must register container api:8080 in the ALB target group."
  }

  assert {
    condition = (
      module.ecs_service.service_settings.health_check_grace_period_seconds == 120 &&
      module.ecs_service.service_settings.deployment_minimum_healthy_percent == 100 &&
      module.ecs_service.service_settings.deployment_maximum_percent == 200 &&
      module.ecs_service.service_settings.circuit_breaker_enable == true &&
      module.ecs_service.service_settings.circuit_breaker_rollback == true
    )
    error_message = "The dev service must use a 120 s grace period, 100/200 percent and a circuit breaker with rollback."
  }

  assert {
    condition     = module.ecs_service.service_settings.enable_execute_command == false
    error_message = "ECS Exec must be off."
  }
}
