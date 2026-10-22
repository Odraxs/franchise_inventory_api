# Plans the Root Composition with the dev Environment values. No AWS account needed.
# terraform test cannot load a -var-file per run, so the variables block below
# mirrors envs/dev.tfvars. Keep both files in sync.
mock_provider "aws" {}

variables {
  environment         = "dev"
  ecr_force_delete    = true
  ecr_max_image_count = 10
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
}
