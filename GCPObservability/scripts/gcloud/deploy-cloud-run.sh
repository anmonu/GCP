#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 1 ]]; then
  echo "Usage: $0 <PROJECT_ID> [REGION]"
  exit 1
fi

PROJECT_ID="$1"
REGION="${2:-asia-southeast1}"

GEMINI_MODEL="${GEMINI_MODEL:-gemini-2.5-pro}"
GCP_LOCATION="${GCP_LOCATION:-${REGION}}"

ELASTIC_URL="${ELASTIC_URL:-http://localhost:9200}"
ELASTIC_USERNAME="${ELASTIC_USERNAME:-}"
ELASTIC_PASSWORD="${ELASTIC_PASSWORD:-}"
ELASTIC_INDEX_NAME="${ELASTIC_INDEX_NAME:-alert-events}"

CHAT_UI_SA="chat-ui-sa@${PROJECT_ID}.iam.gserviceaccount.com"
MONITORING_ADMIN_SA="monitoring-admin-sa@${PROJECT_ID}.iam.gserviceaccount.com"
ALERT_BRIDGE_SA="alert-bridge-sa@${PROJECT_ID}.iam.gserviceaccount.com"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"

deploy_service() {
  local service_name="$1"
  local source_dir="$2"
  local service_account="$3"
  local is_public="$4"
  local env_vars="$5"

  gcloud run deploy "${service_name}" \
    --project "${PROJECT_ID}" \
    --region "${REGION}" \
    --platform managed \
    --source "${ROOT_DIR}/${source_dir}" \
    --service-account "${service_account}" \
    --port 8080 \
    --cpu 1 \
    --memory 512Mi \
    --min-instances 0 \
    --max-instances 3 \
    --set-env-vars "${env_vars}"

  if [[ "${is_public}" == "true" ]]; then
    gcloud run services add-iam-policy-binding "${service_name}" \
      --project "${PROJECT_ID}" \
      --region "${REGION}" \
      --member "allUsers" \
      --role "roles/run.invoker" \
      --quiet >/dev/null
  else
    gcloud run services remove-iam-policy-binding "${service_name}" \
      --project "${PROJECT_ID}" \
      --region "${REGION}" \
      --member "allUsers" \
      --role "roles/run.invoker" \
      --quiet >/dev/null 2>&1 || true
  fi
}

echo "Deploying chat-ui from source (public)..."
deploy_service "chat-ui" "chat-ui" "${CHAT_UI_SA}" "true" "GCP_PROJECT_ID=${PROJECT_ID},GCP_LOCATION=${GCP_LOCATION},GEMINI_MODEL=${GEMINI_MODEL}"

echo "Deploying alert-bridge from source (private)..."
deploy_service "alert-bridge" "alert-bridge" "${ALERT_BRIDGE_SA}" "false" "ELASTIC_URL=${ELASTIC_URL},ELASTIC_USERNAME=${ELASTIC_USERNAME},ELASTIC_PASSWORD=${ELASTIC_PASSWORD},ELASTIC_INDEX_NAME=${ELASTIC_INDEX_NAME}"

echo "Deploying monitoring-admin from source (private)..."
deploy_service "monitoring-admin" "monitoring-admin" "${MONITORING_ADMIN_SA}" "false" "GCP_PROJECT_ID=${PROJECT_ID}"

echo "Granting Pub/Sub push identity invoke access to alert-bridge..."
gcloud run services add-iam-policy-binding "alert-bridge" \
  --project "${PROJECT_ID}" \
  --region "${REGION}" \
  --member "serviceAccount:pubsub-push-invoker@${PROJECT_ID}.iam.gserviceaccount.com" \
  --role "roles/run.invoker" \
  --quiet >/dev/null

echo "Deployment complete."
echo "chat-ui URL: $(gcloud run services describe chat-ui --project "${PROJECT_ID}" --region "${REGION}" --format='value(status.url)')"
echo "monitoring-admin URL: $(gcloud run services describe monitoring-admin --project "${PROJECT_ID}" --region "${REGION}" --format='value(status.url)')"
echo "alert-bridge URL: $(gcloud run services describe alert-bridge --project "${PROJECT_ID}" --region "${REGION}" --format='value(status.url)')"
