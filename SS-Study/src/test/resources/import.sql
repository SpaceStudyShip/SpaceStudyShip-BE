CREATE UNIQUE INDEX IF NOT EXISTS uq_timer_sessions_user_idem ON timer_sessions (user_id, idempotency_key) WHERE idempotency_key IS NOT NULL;
