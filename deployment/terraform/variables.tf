variable "environment" {
  description = "Environment to deploy. It selects the names, tags and settings. Set it in envs/<env>.tfvars."
  type        = string

  validation {
    condition     = contains(["dev", "staging", "prod"], var.environment)
    error_message = "The environment must be one of: dev, staging, prod."
  }
}

variable "aws_region" {
  description = "AWS region for all resources."
  type        = string
  default     = "us-east-1"
}

variable "repository" {
  description = "Source code repository, written to the Repository default tag."
  type        = string
  default     = "github.com/Odraxs/franchise_inventory_api"
}

variable "image_tag" {
  description = "Image Tag (git SHA) to deploy from the Environment's ECR repository. Set it with -var image_tag=<sha> from the output of deployment/scripts/publish-image.sh. Null means no image is published yet (first apply creates only the repository)."
  type        = string
  default     = null

  validation {
    condition     = var.image_tag == null ? true : can(regex("^[0-9a-f]{7,40}$", var.image_tag))
    error_message = "The image_tag must be a git SHA of 7 to 40 lowercase hex characters, or null."
  }
}

variable "ecr_force_delete" {
  description = "Allow destroy to delete the ECR repository while it still holds images. True only in disposable Environments."
  type        = bool
  default     = false
}

variable "ecr_max_image_count" {
  description = "Number of most recent images that the ECR lifecycle policy keeps."
  type        = number
  default     = 10
}

variable "vpc_cidr" {
  description = "IPv4 CIDR block of the Environment's VPC. Each Environment uses its own block, so the VPCs never overlap if they are peered later."
  type        = string
  default     = "10.0.0.0/16"
}

variable "az_count" {
  description = "Number of Availability Zones for the VPC subnets. The ALB and the RDS subnet group need at least 2."
  type        = number
  default     = 2
}

variable "enable_nat_gateway" {
  description = "Create one NAT Gateway so the private subnets can reach the internet. It is billed per hour, so dev keeps it off and runs tasks in public subnets."
  type        = bool
  default     = false
}

variable "enable_budget" {
  description = "Create the monthly AWS Budget (USD 20) with email alerts at 50, 80 and 100 percent. True only in dev."
  type        = bool
  default     = false
}

variable "budget_alert_email" {
  description = "Email address that receives the budget alerts. Never put it in a tfvars file: set it with TF_VAR_budget_alert_email. Required when enable_budget is true."
  type        = string
  default     = null
  sensitive   = true

  validation {
    condition     = !var.enable_budget || can(regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$", var.budget_alert_email))
    error_message = "When enable_budget is true, budget_alert_email must be a valid email address. Set it with TF_VAR_budget_alert_email."
  }
}

variable "db_engine_version" {
  description = "RDS PostgreSQL engine version. Major version 17 only, like the local database."
  type        = string
  default     = "17"
}

variable "db_instance_class" {
  description = "RDS instance class of the Environment's database."
  type        = string
  default     = "db.t4g.micro"
}

variable "db_allocated_storage" {
  description = "Initial RDS storage in GB (gp3, encrypted)."
  type        = number
  default     = 20
}

variable "db_max_allocated_storage" {
  description = "Upper limit in GB for RDS storage autoscaling. Null turns autoscaling off."
  type        = number
  default     = null
}

variable "db_multi_az" {
  description = "Run a Multi-AZ standby for the database."
  type        = bool
  default     = false
}

variable "db_backup_retention_days" {
  description = "Number of days RDS keeps the automated backups (1 to 35)."
  type        = number
  default     = 7
}

variable "db_deletion_protection" {
  description = "Block the deletion of the database. False only in disposable Environments."
  type        = bool
  default     = true
}

variable "db_skip_final_snapshot" {
  description = "Destroy the database without a final snapshot. True only in disposable Environments."
  type        = bool
  default     = false
}

variable "db_secret_recovery_window_days" {
  description = "Recovery window of the DB Secret in days. 0 deletes it at once (dev). Otherwise 7 to 30."
  type        = number
  default     = 30
}

variable "db_password_version" {
  description = "Version of the DB master password. Increase it by 1 to rotate the password in RDS and in the DB Secret in one apply. The same apply also rewrites the host and port in the DB Secret."
  type        = number
  default     = 1
}
