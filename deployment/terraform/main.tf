# Root Composition: one code base, selected per Environment with
# envs/<env>.tfvars and envs/<env>.backend.hcl (no workspaces).

locals {
  project = "franchise-inventory"

  # Every resource name is "<name_prefix>-<resource>", e.g. franchise-inventory-dev-api.
  name_prefix = "${local.project}-${var.environment}"

  # Full image reference for the ECS task definition. Null until an Image Tag is set.
  container_image = var.image_tag == null ? null : "${module.ecr.repository_url}:${var.image_tag}"
}

module "ecr" {
  source = "./modules/ecr"

  repository_name = "${local.name_prefix}-api"
  force_delete    = var.ecr_force_delete
  max_image_count = var.ecr_max_image_count
}
