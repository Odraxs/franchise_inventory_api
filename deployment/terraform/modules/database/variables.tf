variable "name_prefix" {
  description = "Prefix for the name of every resource, for example franchise-inventory-dev."
  type        = string
}

variable "vpc_id" {
  description = "ID of the VPC that holds the database security group."
  type        = string
}

variable "subnet_ids" {
  description = "IDs of the private subnets for the DB subnet group. RDS needs subnets in at least 2 Availability Zones."
  type        = list(string)

  validation {
    condition     = length(var.subnet_ids) >= 2
    error_message = "The subnet_ids must hold at least 2 subnets (RDS needs 2 Availability Zones)."
  }
}

variable "allowed_security_group_ids" {
  description = "Security groups that may connect to PostgreSQL (port 5432). Only the ECS task security group belongs here. An empty list allows no client."
  type        = list(string)
  default     = []
}

variable "engine_version" {
  description = "PostgreSQL engine version. A major version alone (\"17\") lets RDS pick the default minor version. It must stay on major version 17, like the local database."
  type        = string
  default     = "17"

  validation {
    condition     = can(regex("^17(\\.[0-9]+)?$", var.engine_version))
    error_message = "The engine_version must be \"17\" or \"17.<minor>\"."
  }
}

variable "instance_class" {
  description = "RDS instance class, for example db.t4g.micro."
  type        = string
}

variable "allocated_storage" {
  description = "Initial storage in GB (gp3). gp3 needs at least 20 GB."
  type        = number
  default     = 20

  validation {
    condition     = var.allocated_storage >= 20 && floor(var.allocated_storage) == var.allocated_storage
    error_message = "The allocated_storage must be a whole number of 20 or more."
  }
}

variable "max_allocated_storage" {
  description = "Upper limit in GB for storage autoscaling. Null turns autoscaling off."
  type        = number
  default     = null

  validation {
    condition     = var.max_allocated_storage == null ? true : var.max_allocated_storage > var.allocated_storage
    error_message = "The max_allocated_storage must be null or greater than allocated_storage."
  }
}

variable "multi_az" {
  description = "Run a standby instance in a second Availability Zone."
  type        = bool
  default     = false
}

variable "backup_retention_days" {
  description = "Number of days RDS keeps the automated backups (1 to 35)."
  type        = number
  default     = 7

  validation {
    condition     = var.backup_retention_days >= 1 && var.backup_retention_days <= 35 && floor(var.backup_retention_days) == var.backup_retention_days
    error_message = "The backup_retention_days must be a whole number from 1 to 35."
  }
}

variable "deletion_protection" {
  description = "Block the deletion of the instance until this is set to false."
  type        = bool
  default     = true
}

variable "skip_final_snapshot" {
  description = "Delete the instance without a final snapshot. True only in disposable Environments (dev)."
  type        = bool
  default     = false
}

variable "secret_recovery_window_days" {
  description = "Days that Secrets Manager keeps the deleted DB Secret before it removes it. 0 deletes it at once, so the same name can be created again (dev). Otherwise 7 to 30."
  type        = number
  default     = 30

  validation {
    condition     = var.secret_recovery_window_days == 0 || (var.secret_recovery_window_days >= 7 && var.secret_recovery_window_days <= 30)
    error_message = "The secret_recovery_window_days must be 0 or a number from 7 to 30."
  }
}

variable "db_name" {
  description = "Name of the database that RDS creates. The app reads it from the dbname key of the DB Secret."
  type        = string
  default     = "franchise_inventory"
}

variable "master_username" {
  description = "Master user name of the instance. The app reads it from the username key of the DB Secret."
  type        = string
  default     = "franchise_admin"
}

variable "password_version" {
  description = "Version of the master password. Increase it by 1 to generate a new password and write it to RDS and to the DB Secret in the same apply."
  type        = number
  default     = 1

  validation {
    condition     = var.password_version >= 1 && floor(var.password_version) == var.password_version
    error_message = "The password_version must be a whole number of 1 or more."
  }
}
