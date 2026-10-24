# The API Secret of one Environment: a Secrets Manager secret with the API
# Credential (JSON with the keys username and password) of the single trusted
# Client. The app reads it at startup in the aws profile and requires HTTP Basic
# with this pair on every business route.
#
# The password never enters the state, the plan or the code, like the DB Secret:
#   1. An ephemeral resource (module.password) generates it. Ephemeral values
#      are never stored.
#   2. It goes to the secret version through the write-only secret_string_wo, so
#      Terraform sends it and forgets it.
#   3. Terraform sends a write-only value only when secret_string_wo_version
#      changes, so var.credential_version drives the rotation.
# Rotate the API Credential: increase credential_version by 1 and apply. The app
# reads the secret only at startup, so running tasks keep the old credential:
# start a new ECS deployment right after.

module "password" {
  source = "../password"

  providers = {
    aws = aws.ephemeral
  }

  length = var.password_length
}

resource "aws_secretsmanager_secret" "api" {
  name                    = "${var.name_prefix}-api-credential"
  description             = "API Credential (username, password) of the single trusted Client of the ${var.name_prefix} API"
  recovery_window_in_days = var.recovery_window_days
}

# Trade-off of the write-only secret: Terraform writes the JSON only when
# credential_version changes. A new client_username reaches the API Secret only
# with the next credential_version, and that apply also rotates the password.
resource "aws_secretsmanager_secret_version" "api" {
  secret_id = aws_secretsmanager_secret.api.id

  secret_string_wo = jsonencode({
    username = var.client_username
    password = module.password.value
  })
  secret_string_wo_version = var.credential_version
}
