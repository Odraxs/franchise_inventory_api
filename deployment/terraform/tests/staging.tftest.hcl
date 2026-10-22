# Plans the Root Composition with the staging Environment values. No AWS account needed.
# terraform test cannot load a -var-file per run, so the variables block below
# mirrors envs/staging.tfvars. Keep both files in sync.
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
  environment         = "staging"
  ecr_force_delete    = false
  ecr_max_image_count = 10
  vpc_cidr            = "10.20.0.0/16"
  az_count            = 2
  enable_nat_gateway  = true
  enable_budget       = false
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
}
