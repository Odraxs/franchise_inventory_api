# Generates a password as an ephemeral value. Ephemeral values are never written
# to the state or the plan. Used for the DB master password (modules/database)
# and for the API Credential password (modules/api-secret).
#
# This is a separate module with its own provider configuration only because
# terraform test cannot mock ephemeral resources. The tests mock the default
# "aws" provider, give this module's provider (aws.ephemeral in the root) an
# offline configuration and replace the module with override_module. Production
# behavior is the same.
ephemeral "aws_secretsmanager_random_password" "this" {
  password_length = var.length

  # RDS rejects "/", "@", '"' and space in the master password. "$" is also
  # excluded because Spring resolves "${...}" inside imported property values,
  # so a generated password could otherwise break placeholder resolution.
  # The API Credential password leaves out the same characters.
  exclude_characters = "/@\" $"
}
