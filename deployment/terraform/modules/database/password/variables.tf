variable "length" {
  description = "Number of characters of the password. RDS PostgreSQL accepts 8 to 128."
  type        = number
  default     = 32

  validation {
    condition     = var.length >= 8 && var.length <= 128 && floor(var.length) == var.length
    error_message = "The length must be a whole number from 8 to 128."
  }
}
