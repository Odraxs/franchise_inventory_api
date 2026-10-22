# Plans the Root Composition with the prod Environment values. No AWS account needed.
# terraform test cannot load a -var-file per run, so the variables block below
# mirrors envs/prod.tfvars. Keep both files in sync.
mock_provider "aws" {}

variables {
  environment         = "prod"
  ecr_force_delete    = false
  ecr_max_image_count = 30
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
}
