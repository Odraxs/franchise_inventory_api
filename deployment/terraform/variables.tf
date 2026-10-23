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

variable "tasks_in_private_subnets" {
  description = "Run the ECS tasks in the private subnets without a public IP. False runs them in the public subnets with a public IP (dev, no NAT Gateway). True requires enable_nat_gateway, because the tasks reach ECR, CloudWatch Logs and Secrets Manager over the internet."
  type        = bool
  default     = false

  validation {
    condition     = !var.tasks_in_private_subnets || var.enable_nat_gateway
    error_message = "tasks_in_private_subnets = true requires enable_nat_gateway = true. Without a NAT Gateway, private tasks cannot pull the image from ECR."
  }
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

variable "api_client_username" {
  description = "Username of the API Credential of the single trusted Client. Not secret; Terraform writes it with the generated password to the API Secret."
  type        = string
  default     = "inventory-client"
}

variable "api_secret_recovery_window_days" {
  description = "Recovery window of the API Secret in days. 0 deletes it at once (dev). Otherwise 7 to 30. Each Environment uses the window of its DB Secret."
  type        = number
  default     = 30
}

variable "api_credential_version" {
  description = "Version of the API Credential. Increase it by 1 in envs/<env>.tfvars to rotate the password in the API Secret, then force a new ECS deployment. The same apply also rewrites the username."
  type        = number
  default     = 1
}

variable "container_port" {
  description = "Port that the API container listens on (server.port of the app)."
  type        = number
  default     = 8080
}

variable "alb_allowed_ingress_cidrs" {
  description = "IPv4 CIDR blocks that may reach the public ALB. The default allows the whole internet."
  type        = list(string)
  default     = ["0.0.0.0/0"]
}

variable "alb_certificate_arn" {
  description = "ARN of an ACM certificate for the ALB. Empty means HTTP only. Set, it adds HTTPS on port 443 and HTTP redirects to HTTPS. Required in prod, because the API Credential travels in every request with HTTP Basic."
  type        = string
  default     = ""
  nullable    = false

  validation {
    condition     = var.environment != "prod" || var.alb_certificate_arn != ""
    error_message = "prod requires HTTPS: set alb_certificate_arn to an ACM certificate ARN, so HTTP Basic credentials never travel over plain HTTP."
  }
}

variable "ecs_capacity_provider" {
  description = "Fargate capacity provider of the ECS service: FARGATE_SPOT (cheaper, tasks can be reclaimed) or FARGATE (no interruptions)."
  type        = string
  default     = "FARGATE"

  validation {
    condition     = contains(["FARGATE", "FARGATE_SPOT"], var.ecs_capacity_provider)
    error_message = "The ecs_capacity_provider must be FARGATE or FARGATE_SPOT."
  }
}

variable "ecs_desired_count" {
  description = "Number of ECS tasks when the service is created. Later changes are ignored, so Service Auto Scaling can own the count. It must be within autoscaling_min_capacity and autoscaling_max_capacity, so the scaler does not change the count right after the first apply."
  type        = number
  default     = 1

  validation {
    condition = (
      floor(var.ecs_desired_count) == var.ecs_desired_count &&
      var.ecs_desired_count >= var.autoscaling_min_capacity &&
      var.ecs_desired_count <= var.autoscaling_max_capacity
    )
    error_message = "The ecs_desired_count must be a whole number from autoscaling_min_capacity to autoscaling_max_capacity."
  }
}

variable "autoscaling_min_capacity" {
  description = "Lowest number of ECS tasks that Service Auto Scaling keeps running."
  type        = number
  default     = 1

  validation {
    condition     = var.autoscaling_min_capacity >= 1 && floor(var.autoscaling_min_capacity) == var.autoscaling_min_capacity
    error_message = "The autoscaling_min_capacity must be a whole number of 1 or more."
  }
}

variable "autoscaling_max_capacity" {
  description = "Highest number of ECS tasks that Service Auto Scaling may start. It must not be lower than autoscaling_min_capacity."
  type        = number
  default     = 2

  validation {
    condition     = var.autoscaling_max_capacity >= var.autoscaling_min_capacity && floor(var.autoscaling_max_capacity) == var.autoscaling_max_capacity
    error_message = "The autoscaling_max_capacity must be a whole number that is not lower than autoscaling_min_capacity."
  }
}

variable "autoscaling_cpu_target" {
  description = "Average CPU utilization of the ECS tasks, in percent, that the target tracking policy keeps."
  type        = number
  default     = 75

  validation {
    condition     = var.autoscaling_cpu_target >= 1 && var.autoscaling_cpu_target <= 100
    error_message = "The autoscaling_cpu_target must be a percentage from 1 to 100."
  }
}

variable "ecs_log_retention_days" {
  description = "Number of days CloudWatch Logs keeps the container logs."
  type        = number
  default     = 7

  validation {
    condition     = contains([1, 3, 5, 7, 14, 30, 60, 90, 120, 150, 180, 365, 400, 545, 731, 1096, 1827, 2192, 2557, 2922, 3288, 3653], var.ecs_log_retention_days)
    error_message = "The ecs_log_retention_days must be a retention value that CloudWatch Logs supports, for example 7, 14 or 30."
  }
}
