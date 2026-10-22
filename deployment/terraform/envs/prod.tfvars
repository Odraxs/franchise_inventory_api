# prod: durable settings. Destroy never removes images by accident.
# Keep tests/prod.tftest.hcl in sync with these values.
environment = "prod"

# ECR: keep a longer history so older images stay available for rollback.
ecr_force_delete    = false
ecr_max_image_count = 30
