# staging: production-like settings, smaller history.
# Keep tests/staging.tftest.hcl in sync with these values.
environment = "staging"

# ECR
ecr_force_delete    = false
ecr_max_image_count = 10
