#!/bin/bash
set -e

echo "Fetching OAuth secrets from GCP Secret Manager..."

fetch_secret() {
  gcloud secrets versions access latest --secret="$1" 2>/dev/null || echo ""
}

export GCP_PROJECT_ID=$(fetch_secret "gcp-project-id")
export GOOGLE_CLIENT_ID=$(fetch_secret "google-client-id")
export GOOGLE_CLIENT_SECRET=$(fetch_secret "google-client-secret")
export KAKAO_CLIENT_ID=$(fetch_secret "kakao-rest-api-key")
export KAKAO_CLIENT_SECRET=$(fetch_secret "kakao-client-secret")
export NAVER_CLIENT_ID=$(fetch_secret "naver-client-id")
export NAVER_CLIENT_SECRET=$(fetch_secret "naver-client-secret")
export OPENAI_API_KEY=$(fetch_secret "openai-api-key")
export RUNPOD_API_KEY=$(fetch_secret "runpod-api-key")
export RUNPOD_ENDPOINT_URL=$(fetch_secret "runpod-endpoint-url")
export BFL_API_KEY=$(fetch_secret "bfl-api-key")
export CLIP_SERVER_URL=$(fetch_secret "clip-server-url")

# These are hardcoded in application-local.properties, but set here just in case
export DB_URL="jdbc:mysql://localhost:3306/filly_db"
export DB_PASSWORD="1234"
export JWT_SECRET="localDevSecretKeyThatIsAtLeast32CharactersLong!!"

echo "Starting Spring Boot (profile: local)..."
./gradlew bootRun
read -p "Press enter to exit..."