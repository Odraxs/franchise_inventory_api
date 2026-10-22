# Generates the DB master password as an ephemeral value. Ephemeral values are
# never written to the state or the plan.
#
# This is a separate module with its own provider configuration only because
# terraform test cannot mock ephemeral resources. The tests mock the default
# "aws" provider, give this module's provider an offline configuration and
# replace the module with override_module. Production behavior is the same.
ephemeral "aws_secretsmanager_random_password" "this" {
  password_length = var.length

  # RDS rejects "/", "@", '"' and space in the master password.
  exclude_characters = "/@\" "
}
