# Input validation, image selection, task placement and the ALB variants of the Root Composition,
# independent of the Environment.
mock_provider "aws" {
  # Give the repository and the ALB known values during plan, so container_image
  # and alb_url can be asserted.
  override_during = plan

  mock_resource "aws_ecr_repository" {
    defaults = {
      repository_url = "123456789012.dkr.ecr.us-east-1.amazonaws.com/franchise-inventory-dev-api"
    }
  }

  # The provider validates the ARN format of the listener and rule arguments,
  # so the ALB resources need well-formed ARNs instead of random strings.
  mock_resource "aws_lb" {
    defaults = {
      arn      = "arn:aws:elasticloadbalancing:us-east-1:123456789012:loadbalancer/app/franchise-inventory-dev-alb/0123456789abcdef"
      dns_name = "franchise-inventory-dev-alb-123456789.us-east-1.elb.amazonaws.com"
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

  # The mocked data source returns no AZ names by default. The network module needs some.
  mock_data "aws_availability_zones" {
    defaults = {
      names = ["us-east-1a", "us-east-1b", "us-east-1c"]
    }
  }
}

# Mock providers cannot hold ephemeral resources, so the ephemeral password provider
# is a real configuration that cannot reach AWS (fake keys, a closed local endpoint).
# override_module replaces both password modules, so the provider is never called.
provider "aws" {
  alias                       = "ephemeral"
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

override_module {
  target = module.api_secret.module.password
  outputs = {
    value = "test-only-api-password"
  }
}

variables {
  environment = "dev"
}

run "unknown_environment_is_rejected" {
  command = plan

  variables {
    environment = "qa"
  }

  expect_failures = [var.environment]
}

run "image_tag_that_is_not_a_git_sha_is_rejected" {
  command = plan

  variables {
    image_tag = "latest"
  }

  expect_failures = [var.image_tag]
}

run "image_tag_selects_the_image_in_the_environment_repository" {
  command = plan

  variables {
    image_tag = "0123456789ab"
  }

  assert {
    condition     = output.container_image == "123456789012.dkr.ecr.us-east-1.amazonaws.com/franchise-inventory-dev-api:0123456789ab"
    error_message = "container_image must be <ecr_repository_url>:<image_tag>."
  }
}

run "budget_without_alert_email_is_rejected" {
  command = plan

  variables {
    enable_budget = true
  }

  expect_failures = [var.budget_alert_email]
}

run "budget_with_invalid_alert_email_is_rejected" {
  command = plan

  variables {
    enable_budget      = true
    budget_alert_email = "not-an-email"
  }

  expect_failures = [var.budget_alert_email]
}

run "alert_email_is_not_required_without_budget" {
  command = plan

  variables {
    enable_budget = false
  }

  assert {
    condition     = length(aws_budgets_budget.monthly_cost) == 0
    error_message = "Without enable_budget, no budget may be planned."
  }
}

run "alb_without_certificate_serves_http" {
  command = plan

  assert {
    condition     = output.alb_url == "http://franchise-inventory-dev-alb-123456789.us-east-1.elb.amazonaws.com"
    error_message = "Without a certificate, alb_url must be http://<ALB DNS name>."
  }
}

run "alb_with_certificate_serves_https_and_redirects_http" {
  command = plan

  variables {
    alb_certificate_arn = "arn:aws:acm:us-east-1:123456789012:certificate/00000000-0000-0000-0000-000000000000"
  }

  assert {
    condition     = output.alb_url == "https://franchise-inventory-dev-alb-123456789.us-east-1.elb.amazonaws.com"
    error_message = "With a certificate, alb_url must be https://<ALB DNS name>."
  }

  assert {
    condition = module.alb.listeners == [
      { port = 80, protocol = "HTTP", default_action = "redirect" },
      { port = 443, protocol = "HTTPS", default_action = "forward" },
    ]
    error_message = "With a certificate, HTTP must redirect and only HTTPS may forward to the tasks."
  }

  assert {
    condition     = module.alb.actuator_rule.listener_port == 443 && module.alb.actuator_rule.status_code == "404"
    error_message = "With a certificate, the actuator 404 rule must be on the HTTPS listener."
  }

  assert {
    condition     = module.alb.ingress_rules == toset([{ cidr = "0.0.0.0/0", port = 80 }, { cidr = "0.0.0.0/0", port = 443 }])
    error_message = "With a certificate, the ALB must accept ports 80 and 443."
  }
}

run "api_credential_version_drives_the_api_secret_version" {
  command = plan

  variables {
    api_credential_version = 2
  }

  assert {
    condition     = module.api_secret.settings.credential_version == 2
    error_message = "The API Secret version must follow api_credential_version, so a new version rotates the API Credential."
  }

  assert {
    condition     = module.api_secret.stores_plaintext_credential == false
    error_message = "The API Credential must only be written through write-only arguments."
  }
}

run "alb_ingress_is_limited_to_the_allowed_cidrs" {
  command = plan

  variables {
    alb_allowed_ingress_cidrs = ["203.0.113.0/24", "198.51.100.7/32"]
  }

  assert {
    condition     = module.alb.ingress_rules == toset([{ cidr = "203.0.113.0/24", port = 80 }, { cidr = "198.51.100.7/32", port = 80 }])
    error_message = "The ALB must accept port 80 only from alb_allowed_ingress_cidrs."
  }
}

run "no_image_tag_plans_no_task_definition_and_no_service" {
  command = plan

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
    condition     = output.ecs_cluster_name == "franchise-inventory-dev" && output.ecs_log_group_name == "/ecs/franchise-inventory-dev-api"
    error_message = "Without image_tag, the cluster and the log group must still be planned."
  }
}

run "image_tag_plans_the_task_definition_and_the_service" {
  command = plan

  variables {
    image_tag = "0123456789ab"
  }

  assert {
    condition = (
      module.ecs_service.task_definition.containers[0].image == "123456789012.dkr.ecr.us-east-1.amazonaws.com/franchise-inventory-dev-api:0123456789ab" &&
      output.ecs_service_name == "franchise-inventory-dev-api"
    )
    error_message = "With image_tag, the task definition must run <ecr url>:<image_tag> and the service must be planned."
  }
}

run "private_tasks_without_nat_gateway_are_rejected" {
  command = plan

  variables {
    tasks_in_private_subnets = true
    enable_nat_gateway       = false
  }

  expect_failures = [var.tasks_in_private_subnets]
}

run "private_tasks_with_nat_gateway_get_no_public_ip" {
  command = plan

  variables {
    tasks_in_private_subnets = true
    enable_nat_gateway       = true
    image_tag                = "0123456789ab"
  }

  assert {
    condition     = module.ecs_service.service_settings.assign_public_ip == false
    error_message = "Tasks in the private subnets must not get a public IP."
  }
}

run "unknown_capacity_provider_is_rejected" {
  command = plan

  variables {
    ecs_capacity_provider = "EC2"
  }

  expect_failures = [var.ecs_capacity_provider]
}

run "unsupported_log_retention_is_rejected" {
  command = plan

  variables {
    ecs_log_retention_days = 10
  }

  expect_failures = [var.ecs_log_retention_days]
}

run "autoscaling_max_below_min_is_rejected" {
  command = plan

  variables {
    autoscaling_min_capacity = 3
    autoscaling_max_capacity = 2
    ecs_desired_count        = 3
  }

  expect_failures = [var.autoscaling_max_capacity]
}

run "autoscaling_min_of_zero_is_rejected" {
  command = plan

  variables {
    autoscaling_min_capacity = 0
    ecs_desired_count        = 1
  }

  expect_failures = [var.autoscaling_min_capacity]
}

run "desired_count_below_autoscaling_min_is_rejected" {
  command = plan

  variables {
    autoscaling_min_capacity = 2
    autoscaling_max_capacity = 4
    ecs_desired_count        = 1
  }

  expect_failures = [var.ecs_desired_count]
}

run "desired_count_above_autoscaling_max_is_rejected" {
  command = plan

  variables {
    autoscaling_min_capacity = 1
    autoscaling_max_capacity = 2
    ecs_desired_count        = 3
  }

  expect_failures = [var.ecs_desired_count]
}

run "cpu_target_above_100_percent_is_rejected" {
  command = plan

  variables {
    autoscaling_cpu_target = 101
  }

  expect_failures = [var.autoscaling_cpu_target]
}

run "autoscaling_follows_the_capacity_variables" {
  command = plan

  variables {
    image_tag                = "0123456789ab"
    autoscaling_min_capacity = 3
    autoscaling_max_capacity = 6
    ecs_desired_count        = 3
    autoscaling_cpu_target   = 60
  }

  assert {
    condition = (
      module.ecs_service.autoscaling.min_capacity == 3 &&
      module.ecs_service.autoscaling.max_capacity == 6 &&
      module.ecs_service.autoscaling.target_value == 60
    )
    error_message = "The scaling target and policy must use autoscaling_min_capacity, autoscaling_max_capacity and autoscaling_cpu_target."
  }
}
