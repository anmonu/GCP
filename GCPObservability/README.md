# GCP Observability + Gemini (Java/Spring Boot Starter Monorepo)

A production-shaped starter monorepo for:

- Prompting **Gemini 2.5 via Vertex AI** from a simple UI (`chat-ui`)
- Bridging GCP alert notifications from **Pub/Sub push -> Cloud Run** (`alert-bridge`)
- Managing Monitoring resources through a Java REST API (`monitoring-admin`)
- Local observability demo with **Elasticsearch + Kibana + Prometheus + Grafana**

This repository avoids Terraform for Pub/Sub + notification channels and uses `gcloud` scripts instead.
The deployment target is Cloud Run by design (no GKE manifests in this starter).

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
                                          alert-bridge (Cloud Run, private)
                                                        |
                                    +-------------------+------------------+
                                    |                                      |
                                    v                                      v
                              Structured logs                       Elasticsearch index
                                    |                                      |
                                    +--> Prometheus metrics                +--> Kibana

monitoring-admin (Cloud Run, private)
    |
    v
Google Cloud Monitoring APIs
  - list/create/update alert policies
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
- `POST /prompt` invokes Gemini via Vertex AI Java SDK
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
- `GET /api/channels`
- `POST /api/channels/email`
- `POST /api/channels/pubsub`
- `PUT /api/channels/{id}`

Plus health + metrics via Actuator.

## Prerequisites

- Java 21
- Maven 3.9+
- Docker + Docker Compose
- gcloud CLI (authenticated)
- A GCP project with billing enabled

## Local Development

From repo root:

```bash
cd GCPObservability
```

### Run services locally

Terminal 1:

```bash
cd chat-ui
export GCP_PROJECT_ID="<your-project-id>"
export GCP_LOCATION="asia-southeast1"
export GEMINI_MODEL="gemini-2.5-pro"
mvn spring-boot:run
```

Terminal 2:

```bash
cd alert-bridge
export ELASTIC_URL="http://localhost:9200"
mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=8081
```

Terminal 3:

```bash
cd monitoring-admin
export GCP_PROJECT_ID="<your-project-id>"
mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=8082
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

Pub/Sub authenticated push specifics are in:

- `iam/pubsub-push-notes.md`

## Deploy to Cloud Run

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

## Monitoring Admin API Examples

Assume you have a Cloud Run identity token for private service access:

```bash
ADMIN_URL=$(gcloud run services describe monitoring-admin --region <REGION> --format='value(status.url)')
TOKEN=$(gcloud auth print-identity-token --audiences="$ADMIN_URL")
```

List alert policies:

```bash
curl -H "Authorization: Bearer $TOKEN" "$ADMIN_URL/api/alerts"
```

Create alert policy:

```bash
curl -X POST "$ADMIN_URL/api/alerts" \
  -H "Authorization: Bearer $TOKEN" \
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
  -H "Authorization: Bearer $TOKEN" \
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
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "displayName": "Alert PubSub",
    "topicName": "projects/<PROJECT_ID>/topics/monitoring-alert-events",
    "enabled": true
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

3. Notification channel creation fails:
   - Ensure `gcloud beta` components are installed for monitoring channel commands.
   - Confirm Monitoring API is enabled.

4. Elasticsearch indexing errors:
   - Service still processes alerts; check `ELASTIC_URL` and credentials.
   - Validate index visibility in Kibana.

5. Monitoring-admin private API call fails:
   - Use `gcloud auth print-identity-token`.
   - Ensure caller identity can invoke Cloud Run private service.

## Notes on API/SDK Drift

This starter uses current supported Java client paths at the time of creation:

- Google Gen AI Java SDK via `com.google.genai:google-genai` (Vertex AI mode)
- Monitoring APIs via `com.google.cloud:google-cloud-monitoring`

Google Cloud APIs and CLI surface may evolve. If commands drift, update scripts first while preserving least-privilege role intent.
