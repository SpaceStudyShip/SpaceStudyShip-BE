-- todo_categories: 카테고리 (할 일보다 먼저 생성)
CREATE TABLE IF NOT EXISTS todo_categories (
    id          VARCHAR(36)      PRIMARY KEY,
    user_id     BIGINT           NOT NULL,
    name        VARCHAR(20)      NOT NULL,
    icon_id     VARCHAR(50),
    position_x  DOUBLE PRECISION,
    position_y  DOUBLE PRECISION,
    created_at  TIMESTAMP        NOT NULL,
    updated_at  TIMESTAMP        NOT NULL,
    CONSTRAINT fk_todo_categories_member FOREIGN KEY (user_id)
        REFERENCES members(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_todo_categories_user ON todo_categories(user_id);

-- todos: 할 일
CREATE TABLE IF NOT EXISTS todos (
    id                 VARCHAR(36)  PRIMARY KEY,
    user_id            BIGINT       NOT NULL,
    title              VARCHAR(100) NOT NULL,
    scheduled_dates    JSONB        NOT NULL DEFAULT '[]'::jsonb,
    completed_dates    JSONB        NOT NULL DEFAULT '[]'::jsonb,
    category_ids       JSONB        NOT NULL DEFAULT '[]'::jsonb,
    estimated_minutes  INTEGER,
    actual_minutes     INTEGER,
    created_at         TIMESTAMP    NOT NULL,
    updated_at         TIMESTAMP    NOT NULL,
    CONSTRAINT fk_todos_member FOREIGN KEY (user_id)
        REFERENCES members(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_todos_user ON todos(user_id);
