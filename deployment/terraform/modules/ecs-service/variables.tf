variable "name_prefix" {
  description = "Prefix for the name of every resource, for example franchise-inventory-dev."
  type        = string
}

variable "aws_region" {
  description = "AWS region of the Environment. The container gets it in AWS_REGION, and the awslogs driver writes to it."
  type        = string
}

variable "container_image" {
  description = "Full image reference (<ecr repository url>:<image_tag>). Null means no image is published yet: the module then creates no task definition and no service."
  type        = string
  default     = null
}

variable "ecr_repository_arn" {
  description = "ARN of the Environment's ECR repository. The execution role may pull images only from it."
  type        = string
}

variable "vpc_id" {
  description = "ID of the VPC that holds the task security group."
  type        = string
}

variable "subnet_ids" {
  description = "IDs of the subnets for the tasks: public subnets when assign_public_ip is true, private subnets (with a NAT Gateway) when it is false."
  type        = list(string)

  validation {
    condition     = length(var.subnet_ids) >= 1
    error_message = "The subnet_ids must hold at least 1 subnet."
  }
}

variable "assign_public_ip" {
  description = "Give each task a public IP. Required in public subnets without a NAT Gateway, so the tasks can reach ECR, CloudWatch Logs and Secrets Manager."
  type        = bool
}

variable "alb_security_group_id" {
  description = "ID of the ALB security group. It is the only source that may reach the container port."
  type        = string
}

variable "target_group_arn" {
  description = "ARN of the ALB target group (target type ip). The service registers each task in it."
  type        = string
}

variable "db_security_group_id" {
  description = "ID of the database security group. The tasks may open PostgreSQL connections only to it."
  type        = string
}

variable "db_port" {
  description = "Port of the PostgreSQL database."
  type        = number
  default     = 5432
}

variable "db_secret_arn" {
  description = "ARN of the DB Secret. The task role may call secretsmanager:GetSecretValue only on it."
  type        = string
}

variable "db_secret_name" {
  description = "Name of the DB Secret. The container gets it in DB_SECRET_NAME; the aws Spring profile reads the secret at startup."
  type        = string
}

variable "container_port" {
  description = "Port that the API container listens on."
  type        = number
  default     = 8080

  validation {
    condition     = var.container_port >= 1 && var.container_port <= 65535 && floor(var.container_port) == var.container_port
    error_message = "The container_port must be a whole number from 1 to 65535."
  }
}

variable "cpu" {
  description = "CPU units of the task (1024 = 1 vCPU)."
  type        = number
  default     = 512
}

variable "memory" {
  description = "Memory of the task in MiB. It must be a valid Fargate combination with cpu."
  type        = number
  default     = 1024
}

variable "capacity_provider" {
  description = "Fargate capacity provider of the service: FARGATE_SPOT (cheaper, tasks can be reclaimed) or FARGATE (no interruptions)."
  type        = string
  default     = "FARGATE"

  validation {
    condition     = contains(["FARGATE", "FARGATE_SPOT"], var.capacity_provider)
    error_message = "The capacity_provider must be FARGATE or FARGATE_SPOT."
  }
}

variable "desired_count" {
  description = "Number of tasks when the service is created. Terraform ignores later changes, so Service Auto Scaling can own the count."
  type        = number
  default     = 1

  validation {
    condition     = var.desired_count >= 0 && floor(var.desired_count) == var.desired_count
    error_message = "The desired_count must be a whole number of 0 or more."
  }
}

variable "health_check_grace_period_seconds" {
  description = "Seconds that ECS ignores failed ALB health checks after a task starts. Flyway migrations run at startup, so it must cover them."
  type        = number
  default     = 120
}

variable "log_retention_days" {
  description = "Number of days CloudWatch Logs keeps the container logs."
  type        = number
  default     = 7

  validation {
    condition     = contains([1, 3, 5, 7, 14, 30, 60, 90, 120, 150, 180, 365, 400, 545, 731, 1096, 1827, 2192, 2557, 2922, 3288, 3653], var.log_retention_days)
    error_message = "The log_retention_days must be a retention value that CloudWatch Logs supports, for example 7, 14 or 30."
  }
}
