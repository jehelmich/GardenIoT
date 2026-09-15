#!/usr/bin/env bash
# Runs the whole system on a local kind cluster: builds the images, loads them into the
# cluster, installs the Helm chart, and waits for everything to come up.
#
#   scripts/kind-up.sh            # cluster "gardeniot", namespace "gardeniot"
#   CLUSTER=demo scripts/kind-up.sh
set -euo pipefail

CLUSTER=${CLUSTER:-gardeniot}
NAMESPACE=${NAMESPACE:-gardeniot}
TAG=${TAG:-local}
CHART="$(cd "$(dirname "$0")/.." && pwd)/deploy/helm/gardeniot"
cd "$(dirname "$0")/.."

for tool in docker kind helm kubectl; do
  command -v "$tool" >/dev/null || { echo "$tool is required" >&2; exit 1; }
done

if ! kind get clusters 2>/dev/null | grep -qx "$CLUSTER"; then
  kind create cluster --name "$CLUSTER" --wait 120s
fi
kubectl config use-context "kind-$CLUSTER" >/dev/null

for module in controller simulated-device garden-ui; do
  image="ghcr.io/jehelmich/gardeniot-$module:$TAG"
  docker build --quiet --build-arg MODULE="$module" -t "$image" .
  kind load docker-image --name "$CLUSTER" "$image"
done

helm upgrade --install gardeniot "$CHART" \
  --namespace "$NAMESPACE" --create-namespace \
  --set image.tag="$TAG" --set image.pullPolicy=Never \
  --wait --timeout 10m "$@"

echo
kubectl -n "$NAMESPACE" get pods
echo
echo "Garden page: kubectl -n $NAMESPACE port-forward svc/gardeniot-ui 8088:8080          then open http://localhost:8088"
echo "Grafana:     kubectl -n $NAMESPACE port-forward svc/gardeniot-grafana 3000:3000   then open http://localhost:3000/d/gardeniot"
echo "Controller:  kubectl -n $NAMESPACE logs -f deploy/gardeniot-controller"
echo "Tear down:   scripts/kind-down.sh"
