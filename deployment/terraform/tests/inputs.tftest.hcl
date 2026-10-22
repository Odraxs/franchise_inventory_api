# Input validation and image selection of the Root Composition, independent of the Environment.
mock_provider "aws" {
  # Give the repository a known URL during plan, so container_image can be asserted.
  override_during = plan

  mock_resource "aws_ecr_repository" {
    defaults = {
      repository_url = "123456789012.dkr.ecr.us-east-1.amazonaws.com/franchise-inventory-dev-api"
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
