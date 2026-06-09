-- timer_sessions: 공부 타이머 세션 기록
CREATE TABLE IF NOT EXISTS timer_sessions (
    id                VARCHAR(36)  PRIMARY KEY,
    user_id           BIGINT       NOT NULL,
    todo_id           VARCHAR(36),
    todo_title        VARCHAR(100),
    started_at        TIMESTAMP    NOT NULL,
    ended_at          TIMESTAMP    NOT NULL,
    duration_minutes  INTEGER      NOT NULL,
    idempotency_key   VARCHAR(80),
    created_at        TIMESTAMP    NOT NULL,
    updated_at        TIMESTAMP    NOT NULL,
    CONSTRAINT fk_timer_sessions_member FOREIGN KEY (user_id)
        REFERENCES members(id) ON DELETE CASCADE,
    CONSTRAINT chk_timer_duration_positive CHECK (duration_minutes > 0),
    CONSTRAINT chk_timer_duration_max      CHECK (duration_minutes <= 1440),
    CONSTRAINT chk_timer_time_order        CHECK (ended_at > started_at)
);

CREATE INDEX IF NOT EXISTS idx_timer_sessions_user_started
    ON timer_sessions (user_id, started_at DESC);

CREATE INDEX IF NOT EXISTS idx_timer_sessions_user_todo
    ON timer_sessions (user_id, todo_id);

-- Idempotency: 동일 (user, key) 중복 INSERT 방지. key=NULL은 다중 허용 (부분 unique)
CREATE UNIQUE INDEX IF NOT EXISTS uq_timer_sessions_user_idem
    ON timer_sessions (user_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;
