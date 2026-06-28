# filly-backend

AI 기반 개인 일기 서비스 **Filly**의 Spring Boot 백엔드입니다. OAuth2 로그인, JWT 인증, 일기 작성/분석, AI 초안 생성, 추천 카드, 아카이브, 월별 통계, 페르소나/아바타, 공유 콘텐츠 API를 제공합니다.

## 기술 스택

| 분류 | 기술 |
|------|------|
| Language | Java 21 |
| Framework | Spring Boot 4.0.5 |
| Build | Gradle, Spring Boot executable JAR |
| Database | MySQL 8.4, Spring Data JPA |
| Auth | Spring Security, OAuth2 Client, JWT |
| AI | Spring AI, Vertex AI Gemini 2.5 Flash, Vertex AI Imagen |
| Storage | Google Cloud Storage |
| Docs | SpringDoc OpenAPI / Swagger UI |
| Infra | Docker, Google Cloud Run / Cloud SQL |

## 주요 기능

- Google, Kakao, Naver OAuth2 로그인 및 JWT access/refresh 토큰 발급
- 텍스트/이미지/영상 기반 일기 초안 생성 및 일기 저장
- 저장된 일기의 감정, 활동, 장소 등 AI 분석 결과 생성
- 월별 통계 캐싱 및 조회
- 사용자 페르소나 스냅샷 생성, 페르소나 기반 아바타 이미지 생성
- 추천 카드 뽑기, 공개, 셔플, 이력 조회
- 아카이브 폴더 생성 및 일기 연결 관리
- ID 카드, 월별 영수증 형태의 공유 콘텐츠 조회

## 프로젝트 구조

```text
src/main/java/net/coboogie
  archive/          # 아카이브 폴더 및 폴더-일기 연결
  auth/             # OAuth2, JWT, Security 설정
  avatar/           # 페르소나 기반 아바타 생성
  common/           # 공통 응답, 전역 예외 처리, 공통 설정
  config/           # 애플리케이션 설정
  diary/            # 일기 초안, 저장, 수정, 삭제, 미디어 관리
  persona/          # 페르소나 스냅샷
  recommendation/   # 추천 카드 생성/공개/셔플
  share/            # ID 카드, 영수증 공유 콘텐츠
  stat/             # 월별 통계
  user/             # 사용자 정보, 닉네임, 환경설정
  vo/               # JPA Entity
```

```text
src/main/resources
  application.properties          # 기본/운영 설정
  application-local.properties    # 로컬 개발 설정
  prompts/                        # AI 시스템 프롬프트
  schema.sql                      # 스키마 참고용 DDL
  recommendation_schema.sql       # 추천 뽑기 관련 추가 DDL
  static/                         # 간단한 정적 테스트 페이지
```

## 로컬 실행

### 사전 요구사항

- JDK 21
- Docker
- Google Cloud ADC 인증

```bash
gcloud auth application-default login
```

### MySQL 실행

```bash
docker-compose up -d
```

로컬 DB 기본값은 다음과 같습니다.

| 항목 | 값 |
|------|----|
| Host | `localhost:3307` |
| Database | `filly_db` |
| Username | `filly_db` |
| Password | `1234` |

### 애플리케이션 실행

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

GCP Secret Manager에서 로컬 실행용 값을 가져와 실행하려면 다음 스크립트를 사용할 수 있습니다.

```bash
./run-local.sh
```

서버 기본 주소는 `http://localhost:8080/api`입니다.

## 환경 설정

| 프로파일 | 설정 파일 | DB | 주요 용도 |
|----------|-----------|----|-----------|
| default | `application.properties` | Cloud SQL | 운영/배포 |
| local | `application-local.properties` | Docker MySQL `localhost:3307` | 로컬 개발 |

주요 설정 항목:

- `spring.datasource.*`: MySQL 연결 정보
- `jwt.secret`, `jwt.access-token-expiration`, `jwt.refresh-token-expiration`: JWT 설정
- `spring.security.oauth2.client.*`: Google/Kakao/Naver OAuth2 설정
- `spring.cloud.gcp.project-id`: GCP 프로젝트
- `spring.cloud.gcp.storage.bucket`: 일기 미디어 GCS 버킷
- `app.gcs.public-avatar-bucket`: 공개 아바타 이미지 GCS 버킷
- `spring.ai.vertex.ai.gemini.*`: Vertex AI Gemini 설정
- `vertex.ai.imagen.model`: Imagen 모델
- `fastapi.url`: 외부 AI 서버 URL

운영 시 OAuth, JWT, DB, GCP 관련 값은 Secret Manager 또는 환경 변수로 관리하는 것을 권장합니다.

## 빌드와 검증

```bash
./gradlew build
./gradlew test
./gradlew test --tests "net.coboogie.diary.service.DiaryServiceTest"
./gradlew checkstyleMain
./gradlew check
```

Checkstyle 설정은 `config/checkstyle/checkstyle.xml`에 있습니다.

## 인증 흐름

1. 클라이언트가 `/api/oauth2/authorization/{google|kakao|naver}`로 OAuth2 로그인을 시작합니다.
2. OAuth2 Provider 콜백은 `/api/login/oauth2/code/{provider}`로 들어옵니다.
3. `OAuth2SuccessHandler`가 JWT access/refresh 토큰을 발급합니다.
4. 보호 API는 `Authorization: Bearer <access-token>` 헤더를 `JwtAuthenticationFilter`에서 검증합니다.
5. access token 만료 시 `/api/v1/auth/refresh`로 재발급합니다.

허용된 CORS origin은 `SecurityConfig`에서 관리합니다.

## API

모든 API는 `server.servlet.context-path=/api` 아래에서 동작합니다.

### Auth

| Method | Path | 설명 |
|--------|------|------|
| POST | `/api/v1/auth/refresh` | refresh token으로 access token 재발급 |
| POST | `/api/v1/auth/logout` | 로그아웃 |

### User

| Method | Path | 설명 |
|--------|------|------|
| GET | `/api/v1/users/me` | 내 정보 조회 |
| PATCH | `/api/v1/users/me/background-theme` | 배경 테마 변경 |
| PATCH | `/api/v1/users/me/nickname` | 닉네임 변경 |
| PATCH | `/api/v1/users/me/preferences` | 성별, 나이대, AI 초안 어투 등 환경설정 변경 |

### Diary

| Method | Path | 설명 |
|--------|------|------|
| POST | `/api/v1/diaries/draft` | AI 일기 초안 생성 |
| GET | `/api/v1/diaries?year=&month=` | 월별 일기 목록 조회 |
| GET | `/api/v1/diaries/{id}` | 일기 단건 조회 |
| GET | `/api/v1/diaries/all-diaries` | 전체 일기 조회 |
| POST | `/api/v1/diaries` | 일기 저장 |
| PUT | `/api/v1/diaries/{id}` | 일기 본문/이모지 수정 |
| PATCH | `/api/v1/diaries/{id}/star` | 별점 수정 |
| DELETE | `/api/v1/diaries/{id}` | 일기 삭제 |
| POST | `/api/v1/diaries/{id}/media` | 일기 미디어 추가 |
| PUT | `/api/v1/diaries/{id}/media/{mediaId}` | 일기 미디어 교체 |
| DELETE | `/api/v1/diaries/{id}/media/{mediaId}` | 일기 미디어 삭제 |

### Diary V2

| Method | Path | 설명 |
|--------|------|------|
| POST | `/api/v2/diaries/draft` | Gemini 멀티모달 기반 AI 일기 초안 생성 |

### Stats

| Method | Path | 설명 |
|--------|------|------|
| GET | `/api/v1/stats/monthly?year=&month=` | 월별 통계 조회, 캐시가 없으면 즉시 집계 |

### Persona / Avatar

| Method | Path | 설명 |
|--------|------|------|
| GET | `/api/v1/personas` | 페르소나 이력 조회 및 조건 충족 시 자동 생성 |
| POST | `/api/v1/avatars/generate` | 최신 페르소나 기반 아바타 이미지 생성 |

### Recommendation

| Method | Path | 설명 |
|--------|------|------|
| POST | `/api/v1/recommendations/draws` | 추천 카드 3장 생성 |
| GET | `/api/v1/recommendations/draws/{drawId}` | 추천 뽑기 상태 조회 |
| POST | `/api/v1/recommendations/draws/{drawId}/cards/{cardId}/reveal` | 추천 카드 공개 |
| POST | `/api/v1/recommendations/draws/{drawId}/shuffle` | 다른 추천 보기 |
| GET | `/api/v1/recommendations/history` | 공개한 추천 카드 이력 조회 |

### Archive

| Method | Path | 설명 |
|--------|------|------|
| POST | `/api/v1/archives` | 아카이브 폴더 생성 |
| GET | `/api/v1/archives` | 아카이브 폴더 목록 조회 |
| PATCH | `/api/v1/archives/{folderId}` | 폴더 이름/색상 수정 |
| DELETE | `/api/v1/archives/{folderId}` | 폴더 삭제 |
| GET | `/api/v1/archives/{folderId}/diaries` | 폴더 내 일기 목록 조회 |
| POST | `/api/v1/archives/{folderId}/diaries` | 폴더에 일기 추가 |
| DELETE | `/api/v1/archives/{folderId}/diaries/{diaryId}` | 폴더에서 일기 제거 |

### Share

| Method | Path | 설명 |
|--------|------|------|
| GET | `/api/v1/share/id-card` | ID 카드 조회 |
| GET | `/api/v1/share/receipt?year=&month=` | 월별 영수증 조회 |

## 공통 응답 형식

성공 응답:

```json
{
  "success": true,
  "data": {},
  "message": null
}
```

데이터 없는 성공 응답:

```json
{
  "success": true,
  "data": null,
  "message": null
}
```

실패 응답:

```json
{
  "success": false,
  "data": null,
  "message": "오류 메시지"
}
```

## DB 스키마

스키마 참고 파일:

- `schema.sql`
- `src/main/resources/schema.sql`
- `src/main/resources/recommendation_schema.sql`

주요 테이블:

| 테이블 | 설명 |
|--------|------|
| `users` | OAuth 기반 사용자 |
| `diary_entries` | 일기 본문 |
| `diary_media` | 일기 첨부 미디어 |
| `ai_diary_results` | AI 생성 일기 결과 |
| `ai_emotion_analysis` | 일기 감정/활동/장소 분석 |
| `monthly_stats` | 월별 통계 캐시 |
| `persona_snapshots` | 사용자 페르소나 스냅샷 |
| `avatar_history` | 아바타 생성 이력 |
| `archives` | 아카이브 폴더 |
| `archive_entries` | 아카이브 폴더-일기 연결 |
| `recommendations` | 공개된 추천 카드 |
| `recommendation_draws` | 추천 뽑기 세션 |
| `share_contents` | 공유 콘텐츠 |
| `clip_image_tags` | 이미지 태그 분석 결과 |

## Docker

```bash
./gradlew build
docker build -t filly-backend .
docker run -p 8080:8080 filly-backend
```

컨테이너는 `build/libs/filly-backend-0.0.1-SNAPSHOT.jar`를 실행합니다.

## API 문서

서버 실행 후 Swagger UI에서 OpenAPI 문서를 확인할 수 있습니다.

```text
http://localhost:8080/api/swagger-ui/index.html
```
