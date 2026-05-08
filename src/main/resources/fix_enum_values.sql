-- 운영 DB의 오래된 enum 값을 현재 Java enum 이름과 맞추기 위한 보정 DDL.
-- 존재하지 않는 과거 테이블(ai_emotion_analysis, bg_image_history)은 더 이상 대상으로 삼지 않는다.

ALTER TABLE diary_media
    MODIFY COLUMN type ENUM('IMAGE', 'VIDEO') NOT NULL;

ALTER TABLE share_contents
    MODIFY COLUMN type ENUM('ID_CARD', 'RECEIPT', 'KEYWORD_CLOUD') NOT NULL;

ALTER TABLE share_contents
    MODIFY COLUMN status ENUM('PENDING', 'COMPLETED', 'FAILED') NOT NULL;

ALTER TABLE avatar_history
    MODIFY COLUMN status ENUM('PENDING', 'COMPLETED', 'FAILED') NOT NULL;

ALTER TABLE users
    ADD CONSTRAINT uq_users_oauth UNIQUE (oauth_provider, oauth_id);

ALTER TABLE monthly_stats
    ADD CONSTRAINT uq_monthly_stats_user_month UNIQUE (user_id, record_month);
