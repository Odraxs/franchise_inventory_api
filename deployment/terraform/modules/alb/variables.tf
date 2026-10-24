variable "name_prefix" {
  description = "Prefix for the name of every resource, for example franchise-inventory-dev. The ALB and target group names may hold at most 32 characters, so the prefix may hold at most 28."
  type        = string

  validation {
    condition     = length(var.name_prefix) <= 28
    error_message = "The name_prefix must hold at most 28 characters (ALB and target group names are limited to 32)."
  }
}

variable "vpc_id" {
  description = "ID of the VPC that holds the ALB and the targets."
  type        = string
}

variable "vpc_cidr" {
  description = "IPv4 CIDR block of the VPC. The ALB may send traffic only to this block, on the container port."
  type        = string

  validation {
    condition     = can(cidrhost(var.vpc_cidr, 0))
    error_message = "The vpc_cidr must be a valid IPv4 CIDR block."
  }
}

variable "public_subnet_ids" {
  description = "IDs of the public subnets for the ALB. An ALB needs subnets in at least 2 Availability Zones."
  type        = list(string)

  validation {
    condition     = length(var.public_subnet_ids) >= 2
    error_message = "The public_subnet_ids must hold at least 2 subnets (an ALB needs 2 Availability Zones)."
  }
}

variable "container_port" {
  description = "Port that the container listens on. The target group sends traffic and health checks to it."
  type        = number
  default     = 8080

  validation {
    condition     = var.container_port >= 1 && var.container_port <= 65535 && floor(var.container_port) == var.container_port
    error_message = "The container_port must be a whole number from 1 to 65535."
  }
}

variable "health_check_path" {
  description = "Path of the target group health check. The Spring Boot readiness probe answers 200 only when the app can serve requests."
  type        = string
  default     = "/actuator/health/readiness"
}

variable "allowed_ingress_cidrs" {
  description = "IPv4 CIDR blocks that may reach the ALB on port 80 (and 443 when HTTPS is on)."
  type        = list(string)
  default     = ["0.0.0.0/0"]

  validation {
    condition     = length(var.allowed_ingress_cidrs) > 0 && alltrue([for cidr in var.allowed_ingress_cidrs : can(cidrhost(cidr, 0))])
    error_message = "The allowed_ingress_cidrs must hold at least one valid IPv4 CIDR block."
  }
}

variable "certificate_arn" {
  description = "ARN of an ACM certificate. Empty means HTTP only. Set, it adds an HTTPS listener on port 443 and HTTP redirects to HTTPS."
  type        = string
  default     = ""
  nullable    = false

  validation {
    condition     = var.certificate_arn == "" || can(regex("^arn:aws[a-z-]*:acm:[a-z0-9-]+:[0-9]{12}:certificate/.+$", var.certificate_arn))
    error_message = "The certificate_arn must be empty or the ARN of an ACM certificate."
  }
}

variable "ssl_policy" {
  description = "Security policy of the HTTPS listener. Used only when certificate_arn is set."
  type        = string
  default     = "ELBSecurityPolicy-TLS13-1-2-2021-06"
}
