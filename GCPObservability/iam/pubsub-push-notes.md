# Pub/Sub Authenticated Push Notes

For Pub/Sub authenticated push to Cloud Run, three things are required:

1. A push identity service account (this repo uses `pubsub-push-invoker@PROJECT_ID.iam.gserviceaccount.com`).
2. `roles/run.invoker` on the target Cloud Run service (`alert-bridge`) for that push identity service account.
3. The Pub/Sub service agent must be able to mint OIDC tokens for that push identity service account.

Grant for item 3:

```bash
PROJECT_NUMBER=$(gcloud projects describe "$PROJECT_ID" --format='value(projectNumber)')
PUBSUB_SERVICE_AGENT="service-${PROJECT_NUMBER}@gcp-sa-pubsub.iam.gserviceaccount.com"

gcloud iam service-accounts add-iam-policy-binding \
  "pubsub-push-invoker@${PROJECT_ID}.iam.gserviceaccount.com" \
  --member="serviceAccount:${PUBSUB_SERVICE_AGENT}" \
  --role="roles/iam.serviceAccountTokenCreator"
```

No Owner/Editor roles are required.
