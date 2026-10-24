variable "name_prefix" {
  description = "Prefix for the name of every resource, for example franchise-inventory-dev."
  type        = string
}

variable "client_username" {
  description = "Username of the API Credential. Not secret: the password is the secret part. HTTP Basic does not allow a colon in the username."
  type        = string
  default     = "inventory-client"

  validation {
    condition     = can(regex("^[A-Za-z0-9._-]{1,64}$", var.client_username))
    error_message = "The client_username must be 1 to 64 letters, digits, dots, underscores or hyphens."
  }
}

variable "password_length" {
  description = "Number of characters of the API Credential password. At least 32, so the password is a random value of 32 bytes or more."
  type        = number
  default     = 40

  validation {
    condition     = var.password_length >= 32 && var.password_length <= 128 && floor(var.password_length) == var.password_length
    error_message = "The password_length must be a whole number from 32 to 128."
  }
}

variable "recovery_window_days" {
  description = "Days that Secrets Manager keeps the deleted API Secret before it removes it. 0 deletes it at once, so the same name can be created again (dev). Otherwise 7 to 30."
  type        = number
  default     = 30

  validation {
    condition     = var.recovery_window_days == 0 || (var.recovery_window_days >= 7 && var.recovery_window_days <= 30)
    error_message = "The recovery_window_days must be 0 or a number from 7 to 30."
  }
}

variable "credential_version" {
  description = "Version of the API Credential. Increase it by 1 to generate a new password and write it, with client_username, to the API Secret."
  type        = number
  default     = 1

  validation {
    condition     = var.credential_version >= 1 && floor(var.credential_version) == var.credential_version
    error_message = "The credential_version must be a whole number of 1 or more."
  }
}
