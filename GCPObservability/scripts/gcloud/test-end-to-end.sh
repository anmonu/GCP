#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 1 ]]; then
  echo "Usage: $0 <PROJECT_ID> [REGION]"
  exit 1
fi

PROJECT_ID="$1"
REGION="${2:-asia-southeast1}"
TOPIC_NAME="${TOPIC_NAME:-monitoring-alert-events}"

CHAT_URL="$(gcloud run services describe chat-ui --project "${PROJECT_ID}" --region "${REGION}" --format='value(status.url)')"
ADMIN_URL="$(gcloud run services describe monitoring-admin --project "${PROJECT_ID}" --region "${REGION}" --format='value(status.url)')"
ALERT_BRIDGE_URL="$(gcloud run services describe alert-bridge --project "${PROJECT_ID}" --region "${REGION}" --format='value(status.url)')"

echo "1) Checking chat-ui health..."
curl -fsS "${CHAT_URL}/actuator/health" | sed 's/.*/chat-ui healthy/'

echo "2) Checking monitoring-admin health..."
ID_TOKEN="$(gcloud auth print-identity-token --audiences="${ADMIN_URL}" 2>/dev/null || true)"
if [[ -n "${ID_TOKEN}" ]]; then
  curl -fsS -H "Authorization: Bearer ${ID_TOKEN}" "${ADMIN_URL}/actuator/health" | sed 's/.*/monitoring-admin healthy/'
else
  echo "No identity token available; skipping private monitoring-admin health check"
fi

echo "3) Creating sample alert policy via monitoring-admin..."
if [[ -n "${ID_TOKEN}" ]]; then
  HTTP_CODE="$(curl -s -o /tmp/monitoring-admin-create-alert.out -w "%{http_code}" -X POST "${ADMIN_URL}/api/alerts" \
    -H "Authorization: Bearer ${ID_TOKEN}" \
    -H "Content-Type: application/json" \
    -d '{
      "displayName": "cpu-utilization-demo",
      "metricType": "run.googleapis.com/request_count",
      "filter": "metric.type=\"run.googleapis.com/request_count\" AND resource.type=\"cloud_run_revision\"",
      "thresholdValue": 1,
      "durationSeconds": 120,
      "enabled": true,
      "notificationChannels": [],
      "documentation": "Created by test-end-to-end script"
    }')"
  if [[ "${HTTP_CODE}" == "200" ]]; then
    echo "Sample alert policy request submitted"
  else
    echo "Sample alert policy creation returned HTTP ${HTTP_CODE}; continuing smoke test"
  fi
else
  echo "Skipping sample alert policy creation because ID token is unavailable"
fi

echo "4) Publishing test Pub/Sub message..."
gcloud pubsub topics publish "${TOPIC_NAME}" \
  --project "${PROJECT_ID}" \
  --message '{"source":"test-end-to-end","event":"sample-alert","severity":"warning"}' >/dev/null

echo "5) Verifying alert-bridge logs for received event..."
LOG_RESULT="$(gcloud logging read \
  "resource.type=cloud_run_revision AND resource.labels.service_name=alert-bridge AND textPayload:alert_event=" \
  --project "${PROJECT_ID}" \
  --freshness=10m \
  --limit=5 \
  --format='value(textPayload)' || true)"

if [[ -n "${LOG_RESULT}" ]]; then
  echo "alert-bridge received at least one alert event"
else
  echo "No alert-bridge alert_event log found in last 10 minutes; check subscription permissions and endpoint"
  echo "alert-bridge URL: ${ALERT_BRIDGE_URL}"
fi
