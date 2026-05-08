-- 추천 뽑기 API 운영 DB 반영용 DDL
-- application.properties의 ddl-auto=none 환경에서 수동 적용한다.

CREATE TABLE IF NOT EXISTS recommendation_draws (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    current_round INT NOT NULL DEFAULT 1,
    source_period VARCHAR(50) NOT NULL,
    profile_snapshot JSON NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_recommendation_draws_user
        FOREIGN KEY (user_id) REFERENCES users (id)
);

ALTER TABLE recommendations
    ADD COLUMN draw_id BIGINT NULL,
    ADD COLUMN round_no INT NULL,
    ADD COLUMN revealed BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN generation_type VARCHAR(20) NOT NULL DEFAULT 'INITIAL',
    ADD COLUMN updated_at DATETIME NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP;

ALTER TABLE recommendations
    ADD CONSTRAINT fk_recommendations_draw
        FOREIGN KEY (draw_id) REFERENCES recommendation_draws (id);

CREATE INDEX idx_recommendation_draws_user_created
    ON recommendation_draws (user_id, created_at);

CREATE INDEX idx_recommendations_draw_status_card
    ON recommendations (draw_id, status, card_index);

CREATE INDEX idx_recommendations_user_generation_created
    ON recommendations (user_id, generation_type, created_at);
