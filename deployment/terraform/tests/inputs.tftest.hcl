# Input validation, image selection and the ALB variants of the Root Composition,
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
