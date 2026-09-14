#!/usr/bin/env bash
# Deploys GardenIoT to Azure with Terraform: an IoT Hub, a managed identity and two Container Apps.
#
#   az login
#   scripts/deploy-azure.sh                 # apply
#   scripts/deploy-azure.sh destroy         # tear everything down
#   TF_VAR_image_tag=sha-abc1234 scripts/deploy-azure.sh
set -euo pipefail

cd "$(dirname "$0")/../deploy/terraform/azure"

for tool in az terraform; do
  command -v "$tool" >/dev/null || { echo "$tool is required" >&2; exit 1; }
done
az account show >/dev/null 2>&1 || { echo "Run 'az login' first" >&2; exit 1; }
az extension show --name azure-iot >/dev/null 2>&1 || az extension add --name azure-iot --output none

terraform init -input=false

if [ "${1:-apply}" = "destroy" ]; then
  terraform destroy -input=false
  exit 0
fi

terraform apply -input=false
echo
terraform output -raw watch_logs
echo
