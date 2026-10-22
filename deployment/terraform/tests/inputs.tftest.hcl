# Input validation and image selection of the Root Composition, independent of the Environment.
mock_provider "aws" {
  # Give the repository a known URL during plan, so container_image can be asserted.
  override_during = plan

  mock_resource "aws_ecr_repository" {
    defaults = {
      repository_url = "123456789012.dkr.ecr.us-east-1.amazonaws.com/franchise-inventory-dev-api"
    }
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
