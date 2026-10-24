# Cost alarm: a monthly AWS Budget that emails the developer before the Free plan
# credits run out. Only Environments with enable_budget = true create it (dev).

locals {
  budget_limit_usd          = 20
  budget_threshold_percents = [50, 80, 100]
}

resource "aws_budgets_budget" "monthly_cost" {
  count = var.enable_budget ? 1 : 0

  name         = "${local.name_prefix}-monthly-cost"
  budget_type  = "COST"
  limit_amount = tostring(local.budget_limit_usd)
  limit_unit   = "USD"
  time_unit    = "MONTHLY"

  # One email per threshold, on the ACTUAL spend of the month.
  dynamic "notification" {
    for_each = local.budget_threshold_percents

    content {
      comparison_operator        = "GREATER_THAN"
      threshold                  = notification.value
      threshold_type             = "PERCENTAGE"
      notification_type          = "ACTUAL"
      subscriber_email_addresses = [var.budget_alert_email]
    }
  }
}
