#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 1 ]]; then
  echo "Usage: $0 <PROJECT_ID> [REGION] [ALERT_BRIDGE_SERVICE]"
  exit 1
fi

PROJECT_ID="$1"
REGION="${2:-asia-southeast1}"
ALERT_BRIDGE_SERVICE="${3:-alert-bridge}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"

CHAT_ROLE_ID="chatUiGeminiCaller"
MONITORING_ROLE_ID="monitoringAdminOperator"

CHAT_UI_SA="chat-ui-sa@${PROJECT_ID}.iam.gserviceaccount.com"
MONITORING_ADMIN_SA="monitoring-admin-sa@${PROJECT_ID}.iam.gserviceaccount.com"
ALERT_BRIDGE_SA="alert-bridge-sa@${PROJECT_ID}.iam.gserviceaccount.com"
PUSH_INVOKER_SA="pubsub-push-invoker@${PROJECT_ID}.iam.gserviceaccount.com"

ensure_service_account() {
  local sa_name="$1"
  local sa_display="$2"

  if gcloud iam service-accounts describe "${sa_name}@${PROJECT_ID}.iam.gserviceaccount.com" --project "${PROJECT_ID}" >/dev/null 2>&1; then
    echo "Service account ${sa_name} already exists"
  else
    gcloud iam service-accounts create "${sa_name}" \
      --project "${PROJECT_ID}" \
      --display-name "${sa_display}"
    echo "Created service account ${sa_name}"
  fi
}

ensure_custom_role() {
  local role_id="$1"
  local yaml_file="$2"

  if gcloud iam roles describe "${role_id}" --project "${PROJECT_ID}" >/dev/null 2>&1; then
    gcloud iam roles update "${role_id}" \
      --project "${PROJECT_ID}" \
      --file "${yaml_file}"
    echo "Updated custom role ${role_id}"
  else
    gcloud iam roles create "${role_id}" \
      --project "${PROJECT_ID}" \
      --file "${yaml_file}"
    echo "Created custom role ${role_id}"
  fi
}

add_project_binding() {
  local member="$1"
  local role="$2"

  gcloud projects add-iam-policy-binding "${PROJECT_ID}" \
    --member "${member}" \
    --role "${role}" \
    --quiet >/dev/null
}

echo "Enabling required APIs..."
gcloud services enable \
  run.googleapis.com \
  monitoring.googleapis.com \
  pubsub.googleapis.com \
  iam.googleapis.com \
  cloudbuild.googleapis.com \
  artifactregistry.googleapis.com \
  aiplatform.googleapis.com \
  --project "${PROJECT_ID}"

echo "Ensuring service accounts..."
ensure_service_account "chat-ui-sa" "Chat UI runtime identity"
ensure_service_account "monitoring-admin-sa" "Monitoring admin runtime identity"
ensure_service_account "alert-bridge-sa" "Alert bridge runtime identity"
ensure_service_account "pubsub-push-invoker" "Pub/Sub push invoker identity"

echo "Ensuring custom IAM roles..."
ensure_custom_role "${CHAT_ROLE_ID}" "${ROOT_DIR}/iam/chat-ui.custom-role.yaml"
ensure_custom_role "${MONITORING_ROLE_ID}" "${ROOT_DIR}/iam/monitoring-admin.custom-role.yaml"

echo "Binding project-level roles..."
add_project_binding "serviceAccount:${CHAT_UI_SA}" "projects/${PROJECT_ID}/roles/${CHAT_ROLE_ID}"
add_project_binding "serviceAccount:${MONITORING_ADMIN_SA}" "projects/${PROJECT_ID}/roles/${MONITORING_ROLE_ID}"

# Runtime logging/metrics writer is needed for Cloud Run services.
add_project_binding "serviceAccount:${CHAT_UI_SA}" "roles/logging.logWriter"
add_project_binding "serviceAccount:${MONITORING_ADMIN_SA}" "roles/logging.logWriter"
add_project_binding "serviceAccount:${ALERT_BRIDGE_SA}" "roles/logging.logWriter"

echo "Granting Cloud Run invoker on ${ALERT_BRIDGE_SERVICE} to Pub/Sub push identity (if service exists)..."
if gcloud run services describe "${ALERT_BRIDGE_SERVICE}" --region "${REGION}" --project "${PROJECT_ID}" >/dev/null 2>&1; then
  gcloud run services add-iam-policy-binding "${ALERT_BRIDGE_SERVICE}" \
    --region "${REGION}" \
    --project "${PROJECT_ID}" \
    --member "serviceAccount:${PUSH_INVOKER_SA}" \
    --role "roles/run.invoker" \
    --quiet >/dev/null
  echo "Granted run.invoker for Pub/Sub push SA"
else
  echo "Cloud Run service ${ALERT_BRIDGE_SERVICE} not found yet; run this script again after deploy to bind invoker"
fi

echo "Allowing Pub/Sub service agent to mint OIDC tokens as ${PUSH_INVOKER_SA}..."
PROJECT_NUMBER="$(gcloud projects describe "${PROJECT_ID}" --format='value(projectNumber)')"
PUBSUB_SERVICE_AGENT="service-${PROJECT_NUMBER}@gcp-sa-pubsub.iam.gserviceaccount.com"

gcloud iam service-accounts add-iam-policy-binding "${PUSH_INVOKER_SA}" \
  --project "${PROJECT_ID}" \
  --member "serviceAccount:${PUBSUB_SERVICE_AGENT}" \
  --role "roles/iam.serviceAccountTokenCreator" \
  --quiet >/dev/null

echo "Done."
echo "chat-ui SA: ${CHAT_UI_SA}"
echo "monitoring-admin SA: ${MONITORING_ADMIN_SA}"
echo "alert-bridge SA: ${ALERT_BRIDGE_SA}"
echo "pubsub push SA: ${PUSH_INVOKER_SA}"
