# One private RDS PostgreSQL 17 instance for one Environment, plus the DB Secret
# (JSON with the standard RDS keys) that the app reads at startup.
#
# The master password never enters the state, the plan or the code:
#   1. An ephemeral resource (module.password) generates it. Ephemeral values
#      are never stored.
#   2. It goes to RDS (password_wo) and to the secret version (secret_string_wo).
#      Both are write-only arguments, so Terraform sends them and forgets them.
#   3. Terraform sends a write-only value only when its *_wo_version changes.
#      Both versions come from var.password_version, so both writes happen in the
#      same apply with the same ephemeral value.
# Rotate the password: increase password_version by 1 and apply. RDS and the DB
# Secret get the new password together. The app reads the secret only at startup,
# so running tasks keep the old password: start a new ECS deployment right after.

locals {
  port = 5432
}

module "password" {
  source = "../password"

  providers = {
    aws = aws.ephemeral
  }
}

resource "aws_db_subnet_group" "this" {
  name       = "${var.name_prefix}-db"
  subnet_ids = var.subnet_ids

  tags = {
    Name = "${var.name_prefix}-db"
  }
}

# No inline rules and no egress rule. Terraform removes the default "allow all"
# egress rule, and RDS never opens outbound connections. Security groups are
# stateful, so replies to the allowed clients still flow back.
resource "aws_security_group" "db" {
  name        = "${var.name_prefix}-db"
  description = "PostgreSQL access to the ${var.name_prefix} RDS instance"
  vpc_id      = var.vpc_id

  tags = {
    Name = "${var.name_prefix}-db"
  }
}

# count (not for_each): the task security group id is unknown until apply, and
# for_each needs known keys at plan time. The list length is always known.
resource "aws_vpc_security_group_ingress_rule" "postgres" {
  count = length(var.allowed_security_group_ids)

  security_group_id            = aws_security_group.db.id
  description                  = "PostgreSQL from an allowed security group"
  ip_protocol                  = "tcp"
  from_port                    = local.port
  to_port                      = local.port
  referenced_security_group_id = var.allowed_security_group_ids[count.index]
}

# The default parameter group of PostgreSQL 15 and later sets rds.force_ssl = 1,
# so every client must use SSL (the aws profile sets ssl-mode: require).
resource "aws_db_instance" "this" {
  identifier = "${var.name_prefix}-db"

  engine                     = "postgres"
  engine_version             = var.engine_version
  auto_minor_version_upgrade = true
  instance_class             = var.instance_class
  db_name                    = var.db_name
  port                       = local.port

  username            = var.master_username
  password_wo         = module.password.value
  password_wo_version = var.password_version

  storage_type          = "gp3"
  allocated_storage     = var.allocated_storage
  max_allocated_storage = var.max_allocated_storage
  storage_encrypted     = true

  db_subnet_group_name   = aws_db_subnet_group.this.name
  vpc_security_group_ids = [aws_security_group.db.id]
  publicly_accessible    = false
  multi_az               = var.multi_az

  backup_retention_period = var.backup_retention_days
  copy_tags_to_snapshot   = true
  deletion_protection     = var.deletion_protection
  skip_final_snapshot     = var.skip_final_snapshot
  # Fixed name: a second destroy fails until the old final snapshot is deleted or renamed.
  final_snapshot_identifier = var.skip_final_snapshot ? null : "${var.name_prefix}-db-final"

  tags = {
    Name = "${var.name_prefix}-db"
  }
}

resource "aws_secretsmanager_secret" "db" {
  name                    = "${var.name_prefix}-db"
  description             = "Connection data of the ${var.name_prefix} RDS instance (host, port, dbname, username, password)"
  recovery_window_in_days = var.secret_recovery_window_days
}

# Trade-off of the write-only secret: Terraform writes the JSON only when
# password_version changes. If the host or port changes later (for example, the
# instance is replaced), the DB Secret keeps the old values until
# password_version is increased. That apply also rotates the password.
resource "aws_secretsmanager_secret_version" "db" {
  secret_id = aws_secretsmanager_secret.db.id

  secret_string_wo = jsonencode({
    host     = aws_db_instance.this.address
    port     = aws_db_instance.this.port
    dbname   = aws_db_instance.this.db_name
    username = aws_db_instance.this.username
    password = module.password.value
  })
  secret_string_wo_version = var.password_version
}
