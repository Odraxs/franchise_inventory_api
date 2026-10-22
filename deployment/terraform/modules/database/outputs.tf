output "secret_arn" {
  description = "ARN of the DB Secret. Use it to scope the task role's secretsmanager:GetSecretValue permission."
  value       = aws_secretsmanager_secret.db.arn
}

output "secret_name" {
  description = "Name of the DB Secret. The app gets it in the DB_SECRET_NAME environment variable."
  value       = aws_secretsmanager_secret.db.name
}

output "port" {
  description = "Port of the PostgreSQL database."
  value       = local.port
}

output "security_group_id" {
  description = "ID of the database security group."
  value       = aws_security_group.db.id
}

output "instance_settings" {
  description = "Planned settings of the RDS instance and the DB Secret. Exposed so tests can assert them."
  value = {
    engine                      = aws_db_instance.this.engine
    engine_version              = aws_db_instance.this.engine_version
    instance_class              = aws_db_instance.this.instance_class
    allocated_storage           = aws_db_instance.this.allocated_storage
    max_allocated_storage       = aws_db_instance.this.max_allocated_storage
    storage_type                = aws_db_instance.this.storage_type
    storage_encrypted           = aws_db_instance.this.storage_encrypted
    publicly_accessible         = aws_db_instance.this.publicly_accessible
    multi_az                    = aws_db_instance.this.multi_az
    backup_retention_period     = aws_db_instance.this.backup_retention_period
    deletion_protection         = aws_db_instance.this.deletion_protection
    skip_final_snapshot         = aws_db_instance.this.skip_final_snapshot
    final_snapshot_identifier   = aws_db_instance.this.final_snapshot_identifier
    secret_recovery_window_days = aws_secretsmanager_secret.db.recovery_window_in_days
  }
}

output "postgres_ingress_rules" {
  description = "Ingress rules of the database security group as {port, source_security_group_id}. Exposed so tests can assert them."
  value = [
    for rule in aws_vpc_security_group_ingress_rule.postgres : {
      from_port                = rule.from_port
      to_port                  = rule.to_port
      source_security_group_id = rule.referenced_security_group_id
    }
  ]
}

output "stores_plaintext_password" {
  description = "True when the password could reach the state through a non write-only argument. Always false; exposed so tests can assert it."
  value       = nonsensitive(aws_db_instance.this.password != null || aws_secretsmanager_secret_version.db.secret_string != null)
}
