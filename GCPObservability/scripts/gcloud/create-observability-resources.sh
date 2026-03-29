#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 4 ]]; then
  echo "Usage: $0 <PROJECT_ID> <REGION> <ALERT_EMAIL> <ALERT_BRIDGE_SERVICE>"
  exit 1
fi

PROJECT_ID="$1"
REGION="$2"
ALERT_EMAIL="$3"
ALERT_BRIDGE_SERVICE="$4"

TOPIC_NAME="${TOPIC_NAME:-monitoring-alert-events}"
SUBSCRIPTION_NAME="${SUBSCRIPTION_NAME:-monitoring-alert-push-sub}"
EMAIL_CHANNEL_DISPLAY_NAME="${EMAIL_CHANNEL_DISPLAY_NAME:-alert-email-channel}"
PUBSUB_CHANNEL_DISPLAY_NAME="${PUBSUB_CHANNEL_DISPLAY_NAME:-alert-pubsub-channel}"
PUSH_INVOKER_SA="${PUSH_INVOKER_SA:-pubsub-push-invoker@${PROJECT_ID}.iam.gserviceaccount.com}"
ALERT_BRIDGE_PATH="${ALERT_BRIDGE_PATH:-/api/pubsub/alerts}"

ensure_topic() {
  if gcloud pubsub topics describe "${TOPIC_NAME}" --project "${PROJECT_ID}" >/dev/null 2>&1; then
    echo "Pub/Sub topic ${TOPIC_NAME} already exists"
  else
    gcloud pubsub topics create "${TOPIC_NAME}" --project "${PROJECT_ID}"
  fi
}

find_channel_by_display_name() {
  local display_name="$1"
  local channel_type="$2"
  gcloud beta monitoring channels list \
    --project "${PROJECT_ID}" \
    --filter "displayName=\"${display_name}\" AND type=\"${channel_type}\"" \
    --format 'value(name)' | head -n1
}

echo "Creating/validating Pub/Sub topic..."
ensure_topic
TOPIC_FULL_NAME="projects/${PROJECT_ID}/topics/${TOPIC_NAME}"

echo "Creating/validating email notification channel..."
EMAIL_CHANNEL_NAME="$(find_channel_by_display_name "${EMAIL_CHANNEL_DISPLAY_NAME}" "email")"
if [[ -z "${EMAIL_CHANNEL_NAME}" ]]; then
  EMAIL_CHANNEL_NAME="$(gcloud beta monitoring channels create \
    --project "${PROJECT_ID}" \
    --display-name "${EMAIL_CHANNEL_DISPLAY_NAME}" \
    --type "email" \
    --channel-labels "email_address=${ALERT_EMAIL}" \
    --format 'value(name)')"
else
  echo "Email channel already exists: ${EMAIL_CHANNEL_NAME}"
fi

echo "Creating/validating Pub/Sub notification channel..."
PUBSUB_CHANNEL_NAME="$(find_channel_by_display_name "${PUBSUB_CHANNEL_DISPLAY_NAME}" "pubsub")"
if [[ -z "${PUBSUB_CHANNEL_NAME}" ]]; then
  PUBSUB_CHANNEL_NAME="$(gcloud beta monitoring channels create \
    --project "${PROJECT_ID}" \
    --display-name "${PUBSUB_CHANNEL_DISPLAY_NAME}" \
    --type "pubsub" \
    --channel-labels "topic=${TOPIC_FULL_NAME}" \
    --format 'value(name)')"
else
  echo "Pub/Sub channel already exists: ${PUBSUB_CHANNEL_NAME}"
fi

echo "Resolving alert-bridge Cloud Run URL..."
ALERT_BRIDGE_BASE_URL="$(gcloud run services describe "${ALERT_BRIDGE_SERVICE}" --region "${REGION}" --project "${PROJECT_ID}" --format='value(status.url)')"
if [[ -z "${ALERT_BRIDGE_BASE_URL}" ]]; then
  echo "Unable to resolve Cloud Run URL for service ${ALERT_BRIDGE_SERVICE}"
  exit 1
fi

PUSH_ENDPOINT="${ALERT_BRIDGE_BASE_URL}${ALERT_BRIDGE_PATH}"

echo "Creating/validating authenticated push subscription..."
if gcloud pubsub subscriptions describe "${SUBSCRIPTION_NAME}" --project "${PROJECT_ID}" >/dev/null 2>&1; then
  gcloud pubsub subscriptions update "${SUBSCRIPTION_NAME}" \
    --project "${PROJECT_ID}" \
    --push-endpoint "${PUSH_ENDPOINT}" \
    --push-auth-service-account "${PUSH_INVOKER_SA}" \
    --push-auth-token-audience "${ALERT_BRIDGE_BASE_URL}"
else
  gcloud pubsub subscriptions create "${SUBSCRIPTION_NAME}" \
    --project "${PROJECT_ID}" \
    --topic "${TOPIC_NAME}" \
    --push-endpoint "${PUSH_ENDPOINT}" \
    --push-auth-service-account "${PUSH_INVOKER_SA}" \
    --push-auth-token-audience "${ALERT_BRIDGE_BASE_URL}" \
    --ack-deadline 30
fi

echo "Done. Resource summary:"
echo "Topic: ${TOPIC_FULL_NAME}"
echo "Email channel: ${EMAIL_CHANNEL_NAME}"
echo "Pub/Sub channel: ${PUBSUB_CHANNEL_NAME}"
echo "Push subscription: projects/${PROJECT_ID}/subscriptions/${SUBSCRIPTION_NAME}"
echo "Push endpoint: ${PUSH_ENDPOINT}"
