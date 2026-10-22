output "value" {
  description = "The generated password. Ephemeral: pass it only to write-only arguments."
  value       = ephemeral.aws_secretsmanager_random_password.this.random_password
  ephemeral   = true
  sensitive   = true
}
