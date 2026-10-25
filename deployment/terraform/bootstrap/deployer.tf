# Deployer: the IAM user that the CD pipeline (.github/workflows/deploy.yml)
# uses to publish the image and apply the Root Composition.
#
# No aws_iam_access_key here: its secret would be stored in the local state.
# Create the key by hand after the apply (see README, "CD pipeline").

locals {
  deployer_name = "${var.project}-deployer"
  account_id    = data.aws_caller_identity.current.account_id

  # Every role of the Root Composition is named "<project>-<env>-<resource>".
  project_role_arn = "arn:aws:iam::${local.account_id}:role/${var.project}-*"
}

# Permissions boundary of every project role: the most that the ECS execution and
# task roles may ever do, whatever inline policy is put on them. The deployer may
# create and change a project role only with this boundary, so it cannot build a
# role with more rights than itself and pass it to an ECS task or assume it.
# The Root Composition sets it on the roles by name (modules/ecs-service).
resource "aws_iam_policy" "role_boundary" {
  name        = "${var.project}-ecs-role-boundary"
  description = "Permissions boundary of the ${var.project} ECS roles: ECR pull, container logs and the project secrets only"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        # ecr:GetAuthorizationToken does not support resource-level permissions.
        Sid      = "EcrAuthToken"
        Effect   = "Allow"
        Action   = "ecr:GetAuthorizationToken"
        Resource = "*"
      },
      {
        Sid    = "EcrPullProjectRepositories"
        Effect = "Allow"
        Action = [
          "ecr:BatchCheckLayerAvailability",
          "ecr:BatchGetImage",
          "ecr:GetDownloadUrlForLayer",
        ]
        Resource = "arn:aws:ecr:*:${local.account_id}:repository/${var.project}-*"
      },
      {
        Sid      = "WriteProjectLogs"
        Effect   = "Allow"
        Action   = ["logs:CreateLogStream", "logs:PutLogEvents"]
        Resource = "arn:aws:logs:*:${local.account_id}:log-group:/ecs/${var.project}-*"
      },
      {
        Sid      = "ReadProjectSecrets"
        Effect   = "Allow"
        Action   = "secretsmanager:GetSecretValue"
        Resource = "arn:aws:secretsmanager:*:${local.account_id}:secret:${var.project}-*"
      },
    ]
  })
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
# project's role names. A role can get or change rights only while it carries
# the boundary, and the deployer can neither remove the boundary nor assume a role.
resource "aws_iam_user_policy" "deployer_project_roles" {
  name = "manage-project-roles"
  user = aws_iam_user.deployer.name

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "GrantRightsOnlyWithinBoundary"
        Effect = "Allow"
        Action = [
          "iam:CreateRole",
          "iam:PutRolePolicy",
          "iam:PutRolePermissionsBoundary",
        ]
        Resource = local.project_role_arn
        Condition = {
          StringEquals = { "iam:PermissionsBoundary" = aws_iam_policy.role_boundary.arn }
        }
      },
      {
        Sid    = "ManageProjectRoles"
        Effect = "Allow"
        Action = [
          "iam:DeleteRole",
          "iam:GetRole",
          "iam:UpdateRole",
          "iam:UpdateRoleDescription",
          "iam:UpdateAssumeRolePolicy",
          "iam:TagRole",
          "iam:UntagRole",
          "iam:ListRoleTags",
          "iam:GetRolePolicy",
          "iam:DeleteRolePolicy",
          "iam:ListRolePolicies",
          "iam:ListAttachedRolePolicies",
          "iam:ListInstanceProfilesForRole",
        ]
        Resource = local.project_role_arn
      },
      {
        # Same account: a trust policy that names the deployer would let it assume
        # the role without any Allow here, so only an explicit Deny blocks it.
        Sid      = "NeverAssumeProjectRoles"
        Effect   = "Deny"
        Action   = "sts:AssumeRole"
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
