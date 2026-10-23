output "secret_arn" {
  description = "ARN of the API Secret. Use it to scope the task role's secretsmanager:GetSecretValue permission."
  value       = aws_secretsmanager_secret.api.arn
}

output "secret_name" {
  description = "Name of the API Secret. The app gets it in the API_SECRET_NAME environment variable."
  value       = aws_secretsmanager_secret.api.name
}

output "settings" {
  description = "Planned settings of the API Secret and its version. Exposed so tests can assert them."
  value = {
    recovery_window_days = aws_secretsmanager_secret.api.recovery_window_in_days
    credential_version   = aws_secretsmanager_secret_version.api.secret_string_wo_version
  }
}

output "stores_plaintext_credential" {
  description = "True when the API Credential could reach the state through the non write-only secret_string or secret_binary. Always false; exposed so tests can assert it."
  value       = nonsensitive(aws_secretsmanager_secret_version.api.secret_string != null || aws_secretsmanager_secret_version.api.secret_binary != null)
}
