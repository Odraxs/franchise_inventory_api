# The API on ECS Fargate for one Environment: cluster, task definition, service,
# log group, task security group and the two IAM roles (IAM stays in this module).
#
# Two-step first deployment (the image must exist before a task can start):
#   1. apply without image_tag: container_image is null, so this module creates
#      only the parts that need no image (cluster, log group, roles, security group).
#   2. deployment/scripts/publish-image.sh <env> pushes the image, then
#      apply with -var image_tag=<sha>: the task definition and the service appear.
# Every later deployment is step 2 with a new Image Tag (a new task definition revision).

locals {
  # Gate for everything that needs a published image.
  service_count = var.container_image == null ? 0 : 1

  container_name = "api"

  # Only non-secret values: the secret names, not their content. The app reads
  # the DB credentials and the API Credential itself from the DB Secret and the
  # API Secret (Spring Cloud AWS, task role), so there is no "secrets" block.
  container_environment = [
    { name = "SPRING_PROFILES_ACTIVE", value = "aws" },
    { name = "DB_SECRET_NAME", value = var.db_secret_name },
    { name = "API_SECRET_NAME", value = var.api_secret_name },
    { name = "AWS_REGION", value = var.aws_region },
  ]

  # Same trust for both roles: only ECS tasks of this account may assume them.
  ecs_tasks_assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect    = "Allow"
        Principal = { Service = "ecs-tasks.amazonaws.com" }
        Action    = "sts:AssumeRole"
        Condition = {
          StringEquals = { "aws:SourceAccount" = data.aws_caller_identity.current.account_id }
        }
      },
    ]
  })

  # The execution role is used by the ECS agent, not by the app: it pulls the
  # image and ships the container logs. No AWS managed policy is attached.
  execution_role_policy = {
    Version = "2012-10-17"
    Statement = [
      {
        # ecr:GetAuthorizationToken does not support resource-level permissions.
        Sid      = "EcrAuthToken"
        Effect   = "Allow"
        Action   = ["ecr:GetAuthorizationToken"]
        Resource = "*"
      },
      {
        Sid    = "EcrPullEnvironmentRepository"
        Effect = "Allow"
        Action = [
          "ecr:BatchCheckLayerAvailability",
          "ecr:BatchGetImage",
          "ecr:GetDownloadUrlForLayer",
        ]
        Resource = var.ecr_repository_arn
      },
      {
        Sid      = "WriteServiceLogs"
        Effect   = "Allow"
        Action   = ["logs:CreateLogStream", "logs:PutLogEvents"]
        Resource = "${aws_cloudwatch_log_group.this.arn}:*"
      },
    ]
  }

  # The task role is what the app code uses. It may read only its two secrets:
  # the DB Secret and the API Secret. No SSM or ssmmessages permissions: ECS
  # Exec stays off.
  task_role_policy = {
    Version = "2012-10-17"
    Statement = [
      {
        Sid      = "ReadAppSecrets"
        Effect   = "Allow"
        Action   = ["secretsmanager:GetSecretValue"]
        Resource = [var.db_secret_arn, var.api_secret_arn]
      },
    ]
  }
}

data "aws_caller_identity" "current" {}

# ---------------------------------------------------------------------------
# Logs
# ---------------------------------------------------------------------------

resource "aws_cloudwatch_log_group" "this" {
  name              = "/ecs/${var.name_prefix}-api"
  retention_in_days = var.log_retention_days
}

# ---------------------------------------------------------------------------
# IAM
# ---------------------------------------------------------------------------

resource "aws_iam_role" "execution" {
  name                 = "${var.name_prefix}-ecs-execution"
  description          = "ECS agent of ${var.name_prefix}: pull the image from ECR and write the container logs"
  assume_role_policy   = local.ecs_tasks_assume_role_policy
  permissions_boundary = var.role_permissions_boundary_arn
}

resource "aws_iam_role_policy" "execution" {
  name   = "ecr-pull-and-logs"
  role   = aws_iam_role.execution.id
  policy = jsonencode(local.execution_role_policy)
}

resource "aws_iam_role" "task" {
  name                 = "${var.name_prefix}-ecs-task"
  description          = "API of ${var.name_prefix}: read the DB Secret and the API Secret only"
  assume_role_policy   = local.ecs_tasks_assume_role_policy
  permissions_boundary = var.role_permissions_boundary_arn
}

resource "aws_iam_role_policy" "task" {
  name   = "read-app-secrets"
  role   = aws_iam_role.task.id
  policy = jsonencode(local.task_role_policy)
}

# ---------------------------------------------------------------------------
# Task security group
# ---------------------------------------------------------------------------

# The task security group lives here and is created even without an image, so
# the database security group can already reference it. There is no dependency
# cycle: Terraform links modules value by value, not as whole modules. The
# database ingress rule needs this group, and this module needs only the database
# security group and the DB Secret, not the database ingress rule.
resource "aws_security_group" "task" {
  name        = "${var.name_prefix}-task"
  description = "ECS tasks of ${var.name_prefix}: reachable only from the ALB"
  vpc_id      = var.vpc_id

  tags = {
    Name = "${var.name_prefix}-task"
  }
}

# Even with a public IP (dev), only the ALB can reach the tasks.
resource "aws_vpc_security_group_ingress_rule" "from_alb" {
  security_group_id            = aws_security_group.task.id
  description                  = "Container port from the ALB"
  ip_protocol                  = "tcp"
  from_port                    = var.container_port
  to_port                      = var.container_port
  referenced_security_group_id = var.alb_security_group_id
}

# HTTPS to the public AWS endpoints: ECR (API and the S3 image layers), CloudWatch
# Logs and Secrets Manager. There are no VPC endpoints (they cost per hour), so
# the destination is any address. DNS needs no rule: security groups do not filter
# traffic to the Amazon-provided VPC resolver.
resource "aws_vpc_security_group_egress_rule" "https" {
  security_group_id = aws_security_group.task.id
  description       = "HTTPS to the AWS service endpoints"
  ip_protocol       = "tcp"
  from_port         = 443
  to_port           = 443
  cidr_ipv4         = "0.0.0.0/0"
}

resource "aws_vpc_security_group_egress_rule" "postgres" {
  security_group_id            = aws_security_group.task.id
  description                  = "PostgreSQL to the database security group"
  ip_protocol                  = "tcp"
  from_port                    = var.db_port
  to_port                      = var.db_port
  referenced_security_group_id = var.db_security_group_id
}

# ---------------------------------------------------------------------------
# Cluster
# ---------------------------------------------------------------------------

resource "aws_ecs_cluster" "this" {
  name = var.name_prefix

  # Container Insights is off: it bills extra CloudWatch metrics and logs. The
  # basic ECS service metrics (CPU, memory) stay free and are enough for autoscaling.
  setting {
    name  = "containerInsights"
    value = "disabled"
  }
}

# Both providers are available on the cluster. The service picks one per Environment.
resource "aws_ecs_cluster_capacity_providers" "this" {
  cluster_name       = aws_ecs_cluster.this.name
  capacity_providers = ["FARGATE", "FARGATE_SPOT"]
}

# ---------------------------------------------------------------------------
# Task definition and service (only when an image is published)
# ---------------------------------------------------------------------------

resource "aws_ecs_task_definition" "this" {
  count = local.service_count

  family                   = "${var.name_prefix}-api"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = var.cpu
  memory                   = var.memory
  execution_role_arn       = aws_iam_role.execution.arn
  task_role_arn            = aws_iam_role.task.arn

  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = "X86_64"
  }

  container_definitions = jsonencode([
    {
      name      = local.container_name
      image     = var.container_image
      essential = true

      portMappings = [
        {
          containerPort = var.container_port
          protocol      = "tcp"
        },
      ]

      environment = local.container_environment

      logConfiguration = {
        logDriver = "awslogs"
        options = {
          awslogs-group         = aws_cloudwatch_log_group.this.name
          awslogs-region        = var.aws_region
          awslogs-stream-prefix = local.container_name
        }
      }
    },
  ])
}

resource "aws_ecs_service" "this" {
  count = local.service_count

  name            = "${var.name_prefix}-api"
  cluster         = aws_ecs_cluster.this.arn
  task_definition = aws_ecs_task_definition.this[0].arn
  desired_count   = var.desired_count

  capacity_provider_strategy {
    capacity_provider = var.capacity_provider
    weight            = 1
  }

  network_configuration {
    subnets          = var.subnet_ids
    security_groups  = [aws_security_group.task.id]
    assign_public_ip = var.assign_public_ip
  }

  load_balancer {
    target_group_arn = var.target_group_arn
    container_name   = local.container_name
    container_port   = var.container_port
  }

  health_check_grace_period_seconds = var.health_check_grace_period_seconds

  # Start the new tasks first (up to 200 percent), keep all old tasks until the
  # new ones are healthy (100 percent), and roll back a deployment that fails.
  deployment_minimum_healthy_percent = 100
  deployment_maximum_percent         = 200

  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  # No ECS Exec: no shell access into the tasks, so no SSM permissions are needed.
  enable_execute_command = false

  # apply returns when ECS accepts the deployment. The circuit breaker handles a
  # deployment that never becomes healthy.
  wait_for_steady_state = false

  # The capacity provider strategy needs the providers on the cluster first.
  depends_on = [aws_ecs_cluster_capacity_providers.this]

  lifecycle {
    # desired_count sets only the first count. Service Auto Scaling
    # (autoscaling.tf) changes it later, and Terraform must not reset it on
    # every apply. Without this line, each plan would set the count back to
    # var.desired_count and undo a scale-out under load.
    ignore_changes = [desired_count]
  }
}
