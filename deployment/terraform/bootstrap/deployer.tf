# Deployer: the IAM user that the CD pipeline (.github/workflows/deploy.yml)
# uses to publish the image and apply the Root Composition.
#
# No aws_iam_access_key here: its secret would be stored in the local state.
# Create the key by hand after the apply (see README, "CD pipeline").

locals {
  deployer_name = "${var.project}-deployer"

  # Every role of the Root Composition is named "<project>-<env>-<resource>".
  project_role_arn = "arn:aws:iam::${data.aws_caller_identity.current.account_id}:role/${var.project}-*"
}

# force_destroy also deletes the access key created by hand, so destroy does not fail on it.
resource "aws_iam_user" "deployer" {
  name          = local.deployer_name
  force_destroy = true
}

# PowerUserAccess covers every service of the Root Composition (VPC, ECR, RDS,
# ECS, ELB, Secrets Manager, Logs, Budgets, the S3 state bucket) and service-linked
# roles, but no other IAM action.
resource "aws_iam_user_policy_attachment" "deployer_power_user" {
  user       = aws_iam_user.deployer.name
  policy_arn = "arn:aws:iam::aws:policy/PowerUserAccess"
}

# The ECS execution and task roles, with their inline policies, limited to the
# project's role names.
resource "aws_iam_user_policy" "deployer_project_roles" {
  name = "manage-project-roles"
  user = aws_iam_user.deployer.name

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "ManageProjectRoles"
        Effect = "Allow"
        Action = [
          "iam:CreateRole",
          "iam:DeleteRole",
          "iam:GetRole",
          "iam:UpdateRole",
          "iam:UpdateRoleDescription",
          "iam:UpdateAssumeRolePolicy",
          "iam:TagRole",
          "iam:UntagRole",
          "iam:ListRoleTags",
          "iam:PutRolePolicy",
          "iam:GetRolePolicy",
          "iam:DeleteRolePolicy",
          "iam:ListRolePolicies",
          "iam:ListAttachedRolePolicies",
          "iam:ListInstanceProfilesForRole",
        ]
        Resource = local.project_role_arn
      },
      {
        Sid      = "PassProjectRolesToEcsTasks"
        Effect   = "Allow"
        Action   = "iam:PassRole"
        Resource = local.project_role_arn
        Condition = {
          StringEquals = { "iam:PassedToService" = "ecs-tasks.amazonaws.com" }
        }
      },
    ]
  })
}
