-- user_fuel: 유저당 1개 연료 잔량 레코드
CREATE TABLE IF NOT EXISTS user_fuel (
    user_id          BIGINT      PRIMARY KEY,
    current_fuel     INTEGER     NOT NULL DEFAULT 0,
    total_charged    INTEGER     NOT NULL DEFAULT 0,
    total_consumed   INTEGER     NOT NULL DEFAULT 0,
    pending_minutes  INTEGER     NOT NULL DEFAULT 0,
    created_at       TIMESTAMP   NOT NULL,
    updated_at       TIMESTAMP   NOT NULL,
    CONSTRAINT fk_user_fuel_member FOREIGN KEY (user_id)
        REFERENCES members(id) ON DELETE CASCADE,
    CONSTRAINT chk_fuel_non_negative CHECK (current_fuel >= 0),
    CONSTRAINT chk_total_charged_non_negative CHECK (total_charged >= 0),
    CONSTRAINT chk_total_consumed_non_negative CHECK (total_consumed >= 0),
    CONSTRAINT chk_pending_minutes_non_negative CHECK (pending_minutes >= 0)
);

-- fuel_transactions: 충전/소비 거래 내역
CREATE TABLE IF NOT EXISTS fuel_transactions (
    id             VARCHAR(36)  PRIMARY KEY,
    user_id        BIGINT       NOT NULL,
    type           VARCHAR(10)  NOT NULL,
    amount         INTEGER      NOT NULL,
    reason         VARCHAR(30)  NOT NULL,
    reference_id   VARCHAR(50),
    balance_after  INTEGER      NOT NULL,
    created_at     TIMESTAMP    NOT NULL,
    updated_at     TIMESTAMP    NOT NULL,
    CONSTRAINT fk_fuel_transactions_member FOREIGN KEY (user_id)
        REFERENCES members(id) ON DELETE CASCADE,
    CONSTRAINT chk_fuel_tx_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_fuel_tx_type CHECK (type IN ('CHARGE','CONSUME')),
    CONSTRAINT chk_fuel_tx_reason CHECK (reason IN ('STUDY_SESSION','EXPLORATION_UNLOCK'))
);

CREATE INDEX IF NOT EXISTS idx_fuel_transactions_user_created
    ON fuel_transactions (user_id, created_at DESC);
