variable "repository_name" {
  description = "Name of the ECR repository, for example franchise-inventory-dev-api."
  type        = string

  validation {
    condition     = can(regex("^[a-z0-9]+(?:[._-][a-z0-9]+)*$", var.repository_name))
    error_message = "The repository_name must use lowercase letters and digits, separated by single '.', '_' or '-' characters."
  }
}

variable "force_delete" {
  description = "Delete the repository even when it still holds images. Set true only in disposable Environments (dev)."
  type        = bool
  default     = false
}

variable "max_image_count" {
  description = "Number of most recent images that the lifecycle policy keeps. Older images expire."
  type        = number
  default     = 10

  validation {
    condition     = var.max_image_count >= 1 && floor(var.max_image_count) == var.max_image_count
    error_message = "The max_image_count must be a whole number of 1 or more."
  }
}

variable "untagged_image_expiry_days" {
  description = "Number of days after which untagged images (for example, from failed pushes) expire."
  type        = number
  default     = 1

  validation {
    condition     = var.untagged_image_expiry_days >= 1 && floor(var.untagged_image_expiry_days) == var.untagged_image_expiry_days
    error_message = "The untagged_image_expiry_days must be a whole number of 1 or more."
  }
}
