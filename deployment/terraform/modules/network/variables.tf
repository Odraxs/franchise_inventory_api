variable "name_prefix" {
  description = "Prefix for the Name tag of every resource, for example franchise-inventory-dev."
  type        = string
}

variable "vpc_cidr" {
  description = "IPv4 CIDR block of the VPC, for example 10.10.0.0/16."
  type        = string

  validation {
    condition     = can(cidrhost(var.vpc_cidr, 0)) && can(regex("^[0-9.]+/(1[6-9]|2[0-4])$", var.vpc_cidr))
    error_message = "The vpc_cidr must be a valid IPv4 CIDR block with a prefix length from /16 to /24."
  }
}

variable "az_count" {
  description = "Number of Availability Zones to use. Each AZ gets one public and one private subnet. The ALB and the RDS subnet group need at least 2."
  type        = number
  default     = 2

  validation {
    condition     = var.az_count >= 2 && var.az_count <= 4 && floor(var.az_count) == var.az_count
    error_message = "The az_count must be a whole number from 2 to 4."
  }
}

variable "subnet_newbits" {
  description = "Bits added to the VPC prefix length for each subnet. 4 on a /16 VPC gives /20 subnets. 2^subnet_newbits must be at least 2 * az_count."
  type        = number
  default     = 4

  validation {
    condition     = var.subnet_newbits >= 3 && var.subnet_newbits <= 8 && floor(var.subnet_newbits) == var.subnet_newbits
    error_message = "The subnet_newbits must be a whole number from 3 to 8."
  }
}

variable "enable_nat_gateway" {
  description = "Create one NAT Gateway (and its Elastic IP) in the first public subnet and route the private subnets through it. False creates no NAT Gateway and no Elastic IP."
  type        = bool
  default     = false
}
