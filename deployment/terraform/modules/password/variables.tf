variable "length" {
  description = "Number of characters of the password, from 8 to 128 (the limits of the RDS PostgreSQL master password)."
  type        = number
  default     = 32

  validation {
    condition     = var.length >= 8 && var.length <= 128 && floor(var.length) == var.length
    error_message = "The length must be a whole number from 8 to 128."
  }
}
