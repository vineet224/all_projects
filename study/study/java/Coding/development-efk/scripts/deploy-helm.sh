#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

NAMESPACE="${NAMESPACE:-observability}"
RELEASE="${RELEASE:-efk}"

helm dependency update "$ROOT/helm/efk"
helm upgrade --install "$RELEASE" "$ROOT/helm/efk" -n "$NAMESPACE" --create-namespace

kubectl -n "$NAMESPACE" get pods

