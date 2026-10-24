variable "region" {
  description = "AWS region where the state bucket is created."
  type        = string
  default     = "us-east-1"
}

variable "project" {
  description = "Project name. Used as the bucket name prefix and as the Project tag."
  type        = string
  default     = "franchise-inventory"
}

variable "repository" {
  description = "Source repository of this code. Used as the Repository tag."
  type        = string
  default     = "github.com/Odraxs/franchise_inventory_api"
}
