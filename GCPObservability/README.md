# GCP Observability + Gemini (Java/Spring Boot Starter Monorepo)

A production-shaped starter monorepo for:

- Prompting **Gemini 2.5 via Vertex AI** from a simple UI (`chat-ui`)
- Bridging GCP alert notifications from **Pub/Sub push -> alert-bridge endpoint** (`alert-bridge`)
- Managing Monitoring resources through a Java REST API (`monitoring-admin`)
- Local observability demo with **Elasticsearch + Kibana + Prometheus + Grafana**

This repository avoids Terraform for Pub/Sub + notification channels and uses `gcloud` scripts instead.
No GKE manifests are used in this starter.

## Current Working Mode (March 29, 2026)

The repo now supports a hybrid runtime that was validated end-to-end:

- `chat-ui`: deployed to Cloud Run (public)
- `alert-bridge`: running locally in Docker on Mac (exposed via Cloudflare tunnel for Pub/Sub push)
- `monitoring-admin`: running locally in Docker on Mac

This mode is useful for local iteration and Drone-triggered admin workflows without deploying every service to Cloud Run.

## Architecture Overview

```text
[User Browser]
    |
    v
chat-ui (Cloud Run, public)
    |
    v
Vertex AI Gemini (model configurable via GEMINI_MODEL)

GCP Monitoring Alert Policies
    |
    | notifications
    +--> Email Notification Channel
    |
    +--> Pub/Sub Notification Channel -> Pub/Sub Topic -> Push Subscription (OIDC)
                                                        |
                                                        v
                                    alert-bridge (Local Docker via Tunnel URL)
                                                        |
                                    +-------------------+------------------+
                                    |                                      |
                                    v                                      v
                              Structured logs                       Elasticsearch index
                                    |                                      |
                                    +--> Prometheus metrics                +--> Kibana

monitoring-admin (Local Docker)
    |
    v
Google Cloud Monitoring APIs
  - list/create/update alert policies
  - copy/rename alert policies
  - list/create/update notification channels
```

## Repo Structure

```text
GCPObservability/
  README.md
  .gitignore
  ops/
    docker-compose.yml
    .drone.yml
    prometheus/
      prometheus.yml
  scripts/
    gcloud/
      create-service-accounts-and-roles.sh
      create-observability-resources.sh
      deploy-cloud-run.sh
      test-end-to-end.sh
  iam/
    chat-ui.custom-role.yaml
    monitoring-admin.custom-role.yaml
    pubsub-push-notes.md
  chat-ui/
    pom.xml
    Dockerfile
    src/main/java/com/anmonu/gcpobservability/chatui/...
    src/main/resources/...
  alert-bridge/
    pom.xml
    Dockerfile
    src/main/java/com/anmonu/gcpobservability/alertbridge/...
    src/main/resources/...
  monitoring-admin/
    pom.xml
    Dockerfile
    src/main/java/com/anmonu/gcpobservability/monitoringadmin/...
    src/main/resources/...
```

## Services

### 1) chat-ui

- Spring MVC + Thymeleaf UI
- `GET /` prompt form
- `POST /prompt` invokes Gemini via Google Gen AI Java SDK in Vertex AI mode
- Health + metrics via Actuator (`/actuator/health`, `/actuator/prometheus`)

Required env vars:

- `GCP_PROJECT_ID`
- `GCP_LOCATION` (default `asia-southeast1`)
- `GEMINI_MODEL` (default `gemini-2.5-pro`)

### 2) alert-bridge

- `POST /api/pubsub/alerts` receives Pub/Sub push payload
- Decodes base64 message data
- Logs structured alert event JSON
- Attempts Elasticsearch indexing
- Continues gracefully if Elasticsearch is unavailable
- Exposes Prometheus metrics + health endpoint

Required env vars:

- `ELASTIC_URL` (default `http://localhost:9200`)
- `ELASTIC_USERNAME` (optional)
- `ELASTIC_PASSWORD` (optional)
- `ELASTIC_INDEX_NAME` (default `alert-events`)

### 3) monitoring-admin

APIs:

- `GET /api/alerts`
- `POST /api/alerts`
- `PUT /api/alerts/{id}`
- `POST /api/alerts/{id}/copy`
- `POST /api/alerts/{id}/rename`
- `GET /api/channels`
- `POST /api/channels/email`
- `POST /api/channels/pubsub`
- `PUT /api/channels/{id}`

Plus health + metrics via Actuator.

## Prerequisites

- Java 21
- Maven 3.8.5+ (3.9+ recommended)
- Docker + Docker Compose
- gcloud CLI (authenticated)
- A GCP project with billing enabled

## Quickstart (Validated Path)

From repo root:

```bash
cd GCPObservability
```

### 1) Configure environment

```bash
export GCP_PROJECT_ID="gcpobservability"
export GCP_LOCATION="asia-southeast1"
export GEMINI_MODEL="gemini-2.5-flash"
```

### 2) gcloud authentication

```bash
gcloud auth login
gcloud auth application-default login
gcloud config set project "$GCP_PROJECT_ID"
```

### 3) Create service accounts and custom roles

```bash
./scripts/gcloud/create-service-accounts-and-roles.sh "$GCP_PROJECT_ID" "$GCP_LOCATION" alert-bridge
```

### 4) Deploy chat-ui (Cloud Run, public)

```bash
gcloud run deploy chat-ui \
  --project "$GCP_PROJECT_ID" \
  --region "$GCP_LOCATION" \
  --source ./chat-ui \
  --service-account "chat-ui-sa@${GCP_PROJECT_ID}.iam.gserviceaccount.com" \
  --set-env-vars "GCP_PROJECT_ID=${GCP_PROJECT_ID},GCP_LOCATION=${GCP_LOCATION},GEMINI_MODEL=${GEMINI_MODEL}" \
  --allow-unauthenticated
```

### 5) Run alert-bridge locally in Docker

```bash
docker build -t alert-bridge-local ./alert-bridge
docker run --rm -p 8081:8080 \
  -e ELASTIC_URL=http://host.docker.internal:9200 \
  -e ELASTIC_USERNAME= \
  -e ELASTIC_PASSWORD= \
  -e ELASTIC_INDEX_NAME=alert-events \
  --name alert-bridge-local \
  alert-bridge-local
```

Health check:

```bash
curl http://localhost:8081/actuator/health
```

### 6) Expose local alert-bridge for Pub/Sub push

```bash
cloudflared tunnel --url http://localhost:8081
```

Use the generated URL, e.g. `https://<random>.trycloudflare.com`.

### 7) Create/update observability resources (topic/channels/subscription)

```bash
./scripts/gcloud/create-observability-resources.sh "$GCP_PROJECT_ID" "$GCP_LOCATION" "<ALERT_EMAIL>" alert-bridge
```

If you want to directly point subscription to a new tunnel URL:

```bash
PUBLIC_BASE_URL="https://<random>.trycloudflare.com"
gcloud pubsub subscriptions update monitoring-alert-push-sub \
  --project "$GCP_PROJECT_ID" \
  --push-endpoint "${PUBLIC_BASE_URL}/api/pubsub/alerts" \
  --push-auth-service-account "pubsub-push-invoker@${GCP_PROJECT_ID}.iam.gserviceaccount.com" \
  --push-auth-token-audience "${PUBLIC_BASE_URL}"
```

### 8) Run monitoring-admin locally in Docker

Service account key creation is blocked by org policy in this environment. Use ADC impersonation:

```bash
gcloud auth application-default login \
  --impersonate-service-account=monitoring-admin-sa@${GCP_PROJECT_ID}.iam.gserviceaccount.com
```

```bash
docker build -t monitoring-admin-local ./monitoring-admin
docker run --rm -p 8082:8080 \
  -e GCP_PROJECT_ID="$GCP_PROJECT_ID" \
  -e GOOGLE_APPLICATION_CREDENTIALS=/var/secrets/google/adc.json \
  -v "$HOME/.config/gcloud/application_default_credentials.json:/var/secrets/google/adc.json:ro" \
  --name monitoring-admin-local \
  monitoring-admin-local
```

### Start optional local observability stack

```bash
cd ops
docker compose up -d
```

Endpoints:

- Elasticsearch: `http://localhost:9200`
- Kibana: `http://localhost:5601`
- Prometheus: `http://localhost:9090`
- Grafana: `http://localhost:3000` (admin/admin)

Grafana visualization options:

- Add Prometheus datasource (`http://prometheus:9090`) for service metrics
- Add Elasticsearch datasource (`http://elasticsearch:9200`) for indexed alert events

## GCP Authentication & Setup

```bash
gcloud auth login
gcloud auth application-default login
gcloud config set project <your-project-id>
```

## IAM Model (Least Privilege)

Custom roles:

- `iam/chat-ui.custom-role.yaml`
- `iam/monitoring-admin.custom-role.yaml`

Service accounts created by script:

- `chat-ui-sa@PROJECT_ID.iam.gserviceaccount.com`
- `monitoring-admin-sa@PROJECT_ID.iam.gserviceaccount.com`
- `alert-bridge-sa@PROJECT_ID.iam.gserviceaccount.com`
- `pubsub-push-invoker@PROJECT_ID.iam.gserviceaccount.com`

No `roles/owner` or `roles/editor` are granted by the provided scripts.
Scripts are bash-only and idempotent where practical (create-or-update behavior for key resources).
`monitoring-admin-sa` is scoped to Monitoring alert-policy/channel operations via custom role.

Pub/Sub authenticated push specifics are in:

- `iam/pubsub-push-notes.md`

## Deploy to Cloud Run (Optional Full Cloud Mode)

### 1) Create IAM roles + service accounts

```bash
./scripts/gcloud/create-service-accounts-and-roles.sh <PROJECT_ID> [REGION] alert-bridge
```

### 2) Deploy all services

```bash
export GEMINI_MODEL="gemini-2.5-pro"
export GCP_LOCATION="asia-southeast1"

# Optional Elastic config for alert-bridge runtime
export ELASTIC_URL="https://<elastic-host>:9200"
export ELASTIC_USERNAME="<username>"
export ELASTIC_PASSWORD="<password>"

./scripts/gcloud/deploy-cloud-run.sh <PROJECT_ID> [REGION]
```

Default exposure model:

- `chat-ui`: public
- `alert-bridge`: private
- `monitoring-admin`: private

### 3) Create observability resources (no Terraform)

```bash
./scripts/gcloud/create-observability-resources.sh <PROJECT_ID> <REGION> <ALERT_EMAIL> alert-bridge
```

This creates:

- Pub/Sub topic
- Email notification channel
- Pub/Sub notification channel
- Authenticated push subscription to alert-bridge

### 4) Run smoke tests

```bash
./scripts/gcloud/test-end-to-end.sh <PROJECT_ID> [REGION]
```

## Monitoring Admin API Examples (Local Docker)

Base URL:

```bash
ADMIN_URL="http://localhost:8082"
```

List alert policies:

```bash
curl "$ADMIN_URL/api/alerts"
```

Create alert policy:

```bash
curl -X POST "$ADMIN_URL/api/alerts" \
  -H "Content-Type: application/json" \
  -d '{
    "displayName": "cpu-utilization-demo",
    "metricType": "compute.googleapis.com/instance/cpu/utilization",
    "thresholdValue": 0.95,
    "durationSeconds": 120,
    "enabled": true,
    "notificationChannels": [],
    "documentation": "CPU high"
  }'
```

Create email channel:

```bash
curl -X POST "$ADMIN_URL/api/channels/email" \
  -H "Content-Type: application/json" \
  -d '{
    "displayName": "Ops Email",
    "emailAddress": "you@example.com",
    "enabled": true
  }'
```

Create Pub/Sub channel:

```bash
curl -X POST "$ADMIN_URL/api/channels/pubsub" \
  -H "Content-Type: application/json" \
  -d '{
    "displayName": "Alert PubSub",
    "topicName": "projects/<PROJECT_ID>/topics/monitoring-alert-events",
    "enabled": true
  }'
```

Copy an existing alert policy to a new one:

```bash
curl -X POST "$ADMIN_URL/api/alerts/<ALERT_POLICY_ID>/copy" \
  -H "Content-Type: application/json" \
  -d '{
    "displayName": "copied-alert-policy",
    "enabled": true,
    "documentation": "Copied from baseline policy"
  }'
```

Rename an alert policy:

```bash
curl -X POST "$ADMIN_URL/api/alerts/<ALERT_POLICY_ID>/rename" \
  -H "Content-Type: application/json" \
  -d '{
    "displayName": "renamed-alert-policy"
  }'
```

## Observability Flow Notes

- Cloud Monitoring policies can target both Email and Pub/Sub channels.
- Pub/Sub push sends OIDC-authenticated HTTP requests to `alert-bridge`.
- `alert-bridge` emits metrics for receive/index success/failure and logs structured events.
- Elasticsearch + Kibana can store/visualize raw alert payloads.
- Prometheus + Grafana can visualize service health and custom counters.

## Troubleshooting

1. `403` from Vertex AI in `chat-ui`:
   - Verify `chat-ui-sa` has the custom role from `iam/chat-ui.custom-role.yaml`.
   - Confirm `aiplatform.googleapis.com` is enabled.

2. Pub/Sub push not reaching `alert-bridge`:
   - Check subscription push endpoint and audience.
   - Verify `roles/run.invoker` on alert-bridge for `pubsub-push-invoker`.
   - Verify Pub/Sub service agent has `roles/iam.serviceAccountTokenCreator` on `pubsub-push-invoker`.
   - Ensure the local tunnel process is running and URL matches the current subscription endpoint.

3. Notification channel creation fails:
   - Ensure `gcloud beta` components are installed for monitoring channel commands.
   - Confirm Monitoring API is enabled.

4. Elasticsearch indexing errors:
   - Service still processes alerts; check `ELASTIC_URL` and credentials.
   - Validate index visibility in Kibana.

5. Monitoring-admin private API call fails:
   - Use `gcloud auth print-identity-token`.
   - Ensure caller identity can invoke Cloud Run private service.

6. Monitoring-admin local Docker auth fails:
   - Run ADC login with SA impersonation:
     `gcloud auth application-default login --impersonate-service-account=monitoring-admin-sa@<PROJECT_ID>.iam.gserviceaccount.com`
   - Mount ADC JSON into container as documented above.

## Notes on API/SDK Drift

This starter uses current supported Java client paths at the time of creation:

- Google Gen AI Java SDK via `com.google.genai:google-genai` (Vertex AI mode)
- Monitoring APIs via `com.google.cloud:google-cloud-monitoring`

Google Cloud APIs and CLI surface may evolve. If commands drift, update scripts first while preserving least-privilege role intent.
