-- =====================================================
-- V0_0_1: 초기 스키마 생성
-- =====================================================

CREATE TABLE IF NOT EXISTS members
(
    id          BIGSERIAL PRIMARY KEY,
    social_id   VARCHAR(255) NOT NULL,
    social_type VARCHAR(50)  NOT NULL,
    nickname    VARCHAR(255) NOT NULL UNIQUE,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_members_social UNIQUE (social_id, social_type)
);
