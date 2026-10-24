#!/usr/bin/env bash
# Builds the API image and pushes it to the Environment's ECR repository.
#
# Usage:   deployment/scripts/publish-image.sh <dev|staging|prod>
# Output:  the Image Tag on stdout (last line). Pass it to Terraform:
#            terraform apply -var-file=envs/dev.tfvars -var image_tag=<tag>
#
# Non-interactive, so a CD pipeline can run it. Logs go to stderr.
#
# Environment variables:
#   AWS_PROFILE       AWS credentials profile (optional; default credential chain otherwise).
#   AWS_REGION        Region of the ECR repository. Default: us-east-1.
#   ALLOW_DIRTY=1     Publish even when the working tree has uncommitted changes.
#                     The image then does not match the commit in its tag. Use only for testing.
#
# Requirements: aws CLI v2, docker, git. The ECR repository must exist (terraform apply).
set -euo pipefail

readonly PROJECT="franchise-inventory"
readonly REPOSITORY_SUFFIX="api"

log() {
  printf '%s\n' "$*" >&2
}

fail() {
  log "ERROR: $*"
  exit 1
}

usage() {
  log "Usage: $(basename "$0") <dev|staging|prod>"
  exit 2
}

[[ $# -eq 1 ]] || usage
environment="$1"
case "${environment}" in
  dev | staging | prod) ;;
  *) usage ;;
esac

for tool in aws docker git; do
  command -v "${tool}" >/dev/null 2>&1 || fail "'${tool}' is not installed or not on PATH."
done

repo_root="$(git -C "$(dirname "${BASH_SOURCE[0]}")" rev-parse --show-toplevel)"
cd "${repo_root}"

# Image Tag = commit SHA. Refuse a dirty tree, so the tag always matches the code in the image.
if [[ -n "$(git status --porcelain)" && "${ALLOW_DIRTY:-0}" != "1" ]]; then
  fail "The working tree has uncommitted changes. Commit them, or set ALLOW_DIRTY=1."
fi
image_tag="$(git rev-parse --short=12 HEAD)"

region="${AWS_REGION:-us-east-1}"
account_id="$(aws sts get-caller-identity --query Account --output text)"
registry="${account_id}.dkr.ecr.${region}.amazonaws.com"

# Same naming convention as the Root Composition: <project>-<env>-<resource>.
repository_name="${PROJECT}-${environment}-${REPOSITORY_SUFFIX}"
image="${registry}/${repository_name}:${image_tag}"

# Fail early with a clear message if Terraform has not created the repository yet.
aws ecr describe-repositories --region "${region}" --repository-names "${repository_name}" >/dev/null \
  || fail "ECR repository '${repository_name}' not found in ${region}. Run terraform apply for '${environment}' first."

# Tags are IMMUTABLE: pushing an existing tag fails, so stop early with a clear message.
if aws ecr describe-images --region "${region}" --repository-name "${repository_name}" \
  --image-ids imageTag="${image_tag}" >/dev/null 2>&1; then
  fail "Image Tag ${image_tag} already exists in ${repository_name}. Deploy it with -var image_tag=${image_tag}."
fi

log "Logging in to ${registry}"
aws ecr get-login-password --region "${region}" \
  | docker login --username AWS --password-stdin "${registry}" >&2

log "Building ${image}"
docker build --platform linux/amd64 -f deployment/Dockerfile -t "${image}" . >&2

log "Pushing ${image}"
docker push "${image}" >&2

log "Published ${image}"
printf '%s\n' "${image_tag}"
