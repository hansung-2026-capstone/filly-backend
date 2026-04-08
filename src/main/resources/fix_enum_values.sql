-- ENUM 값을 Java enum 이름(대문자)과 일치하도록 수정
-- ddl-auto=validate 환경에서 런타임 오류 방지

-- diary_entries.mode: 'image+text'/'AI+image' → 'IMAGE_TEXT'/'AI_IMAGE', 소문자 → 대문자
ALTER TABLE diary_entries
    MODIFY COLUMN mode ENUM('DEFAULT', 'IMAGE', 'IMAGE_TEXT', 'AI_IMAGE', 'AI') NOT NULL;

-- diary_media.type: 소문자 → 대문자
ALTER TABLE diary_media
    MODIFY COLUMN type ENUM('IMAGE', 'VIDEO') NOT NULL;

-- share_contents.type, status: 소문자 → 대문자
ALTER TABLE share_contents
    MODIFY COLUMN type ENUM('ID_CARD', 'RECEIPT', 'KEYWORD_CLOUD') NOT NULL;
ALTER TABLE share_contents
    MODIFY COLUMN status ENUM('PENDING', 'COMPLETED', 'FAILED') NOT NULL;

-- ai_emotion_analysis.emotion_type: 한국어 → 영어 대문자
ALTER TABLE ai_emotion_analysis
    MODIFY COLUMN emotion_type ENUM('CALM', 'HAPPY', 'ANXIOUS', 'SAD', 'ANGRY') NOT NULL;

-- avatar_history.status: 소문자 → 대문자
ALTER TABLE avatar_history
    MODIFY COLUMN status ENUM('PENDING', 'COMPLETED', 'FAILED') NOT NULL;

-- bg_image_history.trigger_type, status: 소문자 → 대문자
ALTER TABLE bg_image_history
    MODIFY COLUMN trigger_type ENUM('AUTO_INIT', 'USER_REQUEST') NOT NULL;
ALTER TABLE bg_image_history
    MODIFY COLUMN status ENUM('PENDING', 'COMPLETED', 'FAILED') NOT NULL;

-- unique constraint 추가 (아직 없는 경우)
-- users: oauth_provider + oauth_id 복합 유니크
ALTER TABLE users
    ADD CONSTRAINT uq_users_oauth UNIQUE (oauth_provider, oauth_id);

-- monthly_stats: user_id + year_month 복합 유니크
ALTER TABLE monthly_stats
    ADD CONSTRAINT uq_monthly_stats_user_month UNIQUE (user_id, record_month);
