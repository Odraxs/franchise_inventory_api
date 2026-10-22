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
