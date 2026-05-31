# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

```bash
./gradlew build
./gradlew bootRun                                                  # 운영 프로파일
./gradlew bootRun --args='--spring.profiles.active=local'          # 로컬 프로파일
./run-local.sh                                                     # GCP 시크릿 주입 후 로컬 실행

./gradlew test                                                     # 전체 테스트
./gradlew test --tests "net.coboogie.diary.service.DiaryServiceTest"  # 단일 테스트
./gradlew checkstyleMain                                           # Checkstyle 단독 실행
./gradlew check                                                    # Checkstyle + 테스트 (커밋 전 실행)
```

로컬 실행 전제조건: `docker-compose up -d` (MySQL 8.4 on :3307) + GCP 자격증명.

## 패키지 구조 규칙

루트 패키지: `net.coboogie`

```
net.coboogie/
  vo/               ← JPA Entity (클래스명 VO 접미사)
  {domain}/
    controller/
    service/
    repository/
    dto/
  common/
    response/       ← ApiResponse<T> 공통 래퍼
    config/
```

## 코드 컨벤션

- **모든 public 클래스·메서드에 한국어 Javadoc 주석 필수** (`/** */` 형식)
- 비즈니스 로직 주석은 WHY 중심으로 작성
- Checkstyle이 패키지 이름(`net.coboogie.*`), 네이밍, 와일드카드 임포트 금지를 강제 검사함

## 커밋 규칙

- 접두사: `feat / fix / chore / refactor / test`
- `Co-Authored-By` 줄 **절대 추가 금지**
- **커밋 전 반드시 사용자 확인** 후 실행
- pre-commit 훅이 `./gradlew check` 실패 시 커밋 차단

## 공통 응답 형식

```java
ApiResponse.ok(data)  // {"success":true,"data":{...}}
ApiResponse.ok()      // {"success":true,"data":null}
```

## 인증 흐름

1. `/oauth2/authorization/{google|kakao|naver}` → OAuth2 로그인
2. `OAuth2SuccessHandler` → JWT access(15분) + refresh(7일) 발급, HTTP-only 쿠키 저장
3. `JwtAuthenticationFilter` → 모든 보호 엔드포인트에서 Bearer 토큰 검증

## 도메인별 API 현황

### 완료된 API (Base: `/api/v1`)

| 도메인 | 메서드 | 경로 | 설명 |
|--------|--------|------|------|
| Auth | POST | `/auth/refresh` | JWT 리프레시 토큰 재발급 |
| User | GET | `/users/me` | 내 정보 조회 |
| User | PATCH | `/users/me/nickname` | 닉네임 수정 |
| Diary | POST | `/diaries/draft` | AI 일기 초안 생성 (multipart, DB 저장 없음) |
| Diary | POST | `/diaries` | 일기 저장 (multipart) |
| Diary | GET | `/diaries?year=&month=` | 월별 목록 |
| Diary | GET | `/diaries/{id}` | 단건 조회 |
| Diary | GET | `/diaries/all-diaries` | 전체 조회 |
| Diary | PUT | `/diaries/{id}` | rawContent/emoji 수정 |
| Diary | DELETE | `/diaries/{id}` | 삭제 |
| Diary V2 | POST | `/v2/diaries/draft` | AI 일기 초안 생성 v2 (Gemini 멀티모달, STT/BLIP 전처리 없음) |
| Avatar | POST | `/avatars/generate` | 최신 페르소나 기반 아바타 이미지 생성 (10~30초) |
| Persona | GET | `/personas` | 페르소나 이력 조회 (조건 충족 시 자동 생성) |
| Share | GET | `/share/id-card` | ID 카드 조회 (아바타 URL·닉네임·키워드) |
| Share | GET | `/share/receipt?year=&month=` | 월별 영수증 조회 |
| Recommendation | POST | `/recommendations/draws` | 추천 뽑기 시작 (카드 3장 생성) |
| Recommendation | GET | `/recommendations/draws/{drawId}` | 추천 뽑기 상태 조회 |
| Recommendation | POST | `/recommendations/draws/{drawId}/cards/{cardId}/reveal` | 추천 카드 공개 |
| Recommendation | POST | `/recommendations/draws/{drawId}/shuffle` | 다른 추천 보기 (하루 3회 제한) |
| Recommendation | GET | `/recommendations/history` | 공개한 추천 카드 이력 |
| Archive | POST | `/archives` | 폴더 생성 |
| Archive | GET | `/archives` | 폴더 목록 (일기 수 포함) |
| Archive | PATCH | `/archives/{folderId}` | 폴더 이름·색상 수정 |
| Archive | DELETE | `/archives/{folderId}` | 폴더 삭제 (연결만 제거) |
| Archive | GET | `/archives/{folderId}/diaries` | 폴더 내 일기 목록 |
| Archive | POST | `/archives/{folderId}/diaries` | 폴더에 일기 추가 |
| Archive | DELETE | `/archives/{folderId}/diaries/{diaryId}` | 폴더에서 일기 제거 |
| Stats | GET | `/stats/monthly?year=&month=` | 월별 통계 조회 (캐시 미존재 시 즉시 집계) |

> Diary V2는 Base `/api/v2`, 나머지는 모두 `/api/v1`.

## AI 설정

- **모델**: Gemini 2.5 Flash (Vertex AI, `asia-northeast3`)
- **일기 ChatClient**: `AiConfig` 빈 1개, `classpath:prompts/diary-system.txt` defaultSystem
- **페르소나 ChatClient**: 별도 빈 필요 (`@Qualifier("personaChatClient")`)
- **호출 구조**: `DiaryService` → `AiDraftGeneratorService` → `chatClient`
- BLIP 이미지 분석: `ImageAnalysisService.analyzeCaption(file)` → caption 문자열
- STT: `SpeechToTextService.transcribe(file)` → 텍스트

## DB / 인프라

| 프로파일 | 설정 위치 | ddl-auto |
|---------|-----------|----------|
| 운영(default) | `application.properties` (Cloud SQL) | `none` |
| local | `application-local.properties` (Docker :3307) | `update` |

GCP 프로젝트 ID·GCS 버킷명은 `application.properties` 참고.
DDL 전체: `schema.sql` (루트)
