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

variables {
  environment         = "dev"
  ecr_force_delete    = true
  ecr_max_image_count = 10
  vpc_cidr            = "10.10.0.0/16"
  az_count            = 2
  enable_nat_gateway  = false
  enable_budget       = true

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
}
