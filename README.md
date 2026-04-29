# filly-backend

AI 기반 개인 일기 서비스 **Filly**의 백엔드 서버입니다.

## 기술 스택

| 분류 | 기술 |
|------|------|
| Language | Java 21 |
| Framework | Spring Boot 4.0.5 |
| Build | Gradle (WAR) |
| DB | MySQL 8.4 (Cloud SQL / Docker) |
| ORM | Spring Data JPA |
| Auth | Spring Security, OAuth2 (Google · Kakao · Naver), JWT |
| AI | Gemini 2.5 Flash via Vertex AI (Spring AI) |
| Storage | Google Cloud Storage |
| Speech | Google Cloud Speech-to-Text |
| Image | BLIP 이미지 캡션 분석 |
| Docs | SpringDoc OpenAPI (Swagger UI) |
| Infra | GCP Cloud Run, Docker |

## 프로젝트 구조

```
net.coboogie/
  vo/               # JPA Entity (클래스명 VO 접미사)
  auth/             # OAuth2 로그인, JWT 필터
  diary/            # 일기 CRUD, AI 초안 생성
  user/             # 사용자 정보·테마
  stat/             # 월별 통계 집계
  share/            # ID카드·영수증 공유 콘텐츠
  persona/          # AI 페르소나 스냅샷
  blip/             # 이미지 캡션 분석
  common/           # ApiResponse, 전역 예외처리, 공통 설정
```

## 로컬 개발 환경 설정

### 사전 요건

- JDK 21
- Docker
- GCP 자격증명 (`gcloud auth application-default login`)

### 실행

```bash
# 1. MySQL 컨테이너 시작 (포트 3307)
docker-compose up -d

# 2. 로컬 프로파일로 서버 실행 (GCP 시크릿 주입 포함)
./run-local.sh

# 또는 Gradle로 직접 실행
./gradlew bootRun --args='--spring.profiles.active=local'
```

### 테스트 및 정적 분석

```bash
./gradlew test                                                      # 전체 테스트
./gradlew test --tests "net.coboogie.diary.service.DiaryServiceTest" # 단일 테스트
./gradlew checkstyleMain                                            # Checkstyle 단독 실행
./gradlew check                                                     # Checkstyle + 테스트
```

커밋 전 pre-commit 훅이 `./gradlew check`를 자동 실행합니다.

## 환경 설정

| 프로파일 | DB | ddl-auto |
|----------|----|----------|
| 운영 (default) | Cloud SQL | `none` |
| local | Docker MySQL (`:3307`) | `update` |

GCP 프로젝트 ID, GCS 버킷명 등 인프라 설정은 `src/main/resources/application.properties`를 참고하세요.

## 인증 흐름

```
클라이언트
  └─ GET /oauth2/authorization/{google|kakao|naver}
       └─ OAuth2SuccessHandler
            └─ JWT access(15분) + refresh(7일) → HTTP-only 쿠키
POST /api/v1/auth/refresh   # 토큰 재발급
```

모든 보호 엔드포인트는 `JwtAuthenticationFilter`가 Bearer 토큰을 검증합니다.

## API 목록 (Base: `/api/v1`)

### Auth
| 메서드 | 경로 | 설명 |
|--------|------|------|
| POST | `/auth/refresh` | JWT 리프레시 토큰 재발급 |

### User
| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/users/me` | 내 정보 조회 |
| PATCH | `/users/me/nickname` | 닉네임 수정 |
| PATCH | `/users/me/background-theme` | 배경 테마 변경 |

### Diary
| 메서드 | 경로 | 설명 |
|--------|------|------|
| POST | `/diaries/draft` | AI 일기 초안 생성 (multipart, DB 저장 없음) |
| POST | `/diaries` | 일기 저장 (multipart) |
| GET | `/diaries?year=&month=` | 월별 목록 |
| GET | `/diaries/{id}` | 단건 조회 |
| GET | `/diaries/all-diaries` | 전체 조회 |
| PUT | `/diaries/{id}` | rawContent / emoji 수정 |
| PATCH | `/diaries/{id}/star` | 별점 업데이트 |
| DELETE | `/diaries/{id}` | 삭제 |

### Stats
| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/stats/monthly?year=&month=` | 월별 통계 조회 (없으면 즉시 집계) |

### Share
| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/share/id-card` | ID카드 (`avatar_url`, `nickname`, `keywords[]`) |
| GET | `/share/receipt?year=&month=` | 월별 영수증 JSON |

## 공통 응답 형식

```json
{ "success": true, "data": { ... } }
{ "success": true, "data": null }
```

## DB 스키마

`schema.sql` (루트)에 전체 DDL이 정의되어 있습니다.

주요 테이블:

| 테이블 | 설명 |
|--------|------|
| `users` | 사용자 (OAuth 기반) |
| `diary_entries` | 일기 본문 |
| `diary_media` | 첨부 이미지·영상 (GCS URL) |
| `ai_diary_results` | AI 생성 일기 텍스트 |
| `ai_diary_analysis` | 감정·활동·장소 분석 결과 |
| `monthly_stats` | 월별 통계 캐시 |
| `persona_snapshots` | AI 페르소나 스냅샷 |
| `archives` / `archive_entries` | 아카이브 |
| `recommendations` | IAB 카테고리 기반 추천 |
| `share_contents` | 공유 콘텐츠 (ID카드·영수증) |

## Docker 배포

```bash
./gradlew build
docker build -t filly-backend .
docker run -p 8080:8080 filly-backend
```

## API 문서

서버 실행 후 `/swagger-ui/index.html`에서 Swagger UI를 확인할 수 있습니다.
