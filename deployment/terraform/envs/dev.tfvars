# dev: disposable and cheap. The only Environment that is deployed for now.
# Keep tests/dev.tftest.hcl in sync with these values.
environment = "dev"

# ECR
ecr_force_delete    = true
ecr_max_image_count = 10
