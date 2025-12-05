-- ==================================================================================
-- V1__Init_Aldebaran.sql
-- Initial schema for Aldebaran Training Microservice (PostgreSQL)
-- ==================================================================================

-- ----------------------------------------------------------------------------------
-- 1. ANATOMY & MOVEMENT LIBRARY
-- ----------------------------------------------------------------------------------

CREATE TABLE muscles
(
    id             BIGSERIAL PRIMARY KEY,
    medical_name   VARCHAR(100) NOT NULL UNIQUE,
    common_name_en VARCHAR(100),
    common_name_fr VARCHAR(100),
    description_en TEXT,
    description_fr TEXT,
    muscle_group   VARCHAR(50)  NOT NULL
);

CREATE TABLE movements
(
    id                  VARCHAR(20) PRIMARY KEY,   -- Business Key (e.g., WL-SQ-001)

    name                VARCHAR(50)      NOT NULL,
    name_abbreviation   VARCHAR(20),

    category            VARCHAR(30)      NOT NULL, -- Renamed from 'family' to 'category'

    -- Load Logic
    involves_bodyweight BOOLEAN          NOT NULL DEFAULT FALSE,
    bodyweight_factor   DOUBLE PRECISION NOT NULL DEFAULT 0.0,

    -- Content
    description_en      TEXT,
    description_fr      TEXT,
    coaching_cues_en    TEXT,
    coaching_cues_fr    TEXT,
    video_url           VARCHAR(512),
    image_url           VARCHAR(512),

    -- Audit
    created_at          TIMESTAMP        NOT NULL,
    updated_at          TIMESTAMP        NOT NULL
);

-- Weighted Join Table for Anatomy
CREATE TABLE movement_muscles
(
    id            BIGSERIAL PRIMARY KEY,
    movement_id   VARCHAR(20)      NOT NULL REFERENCES movements (id),
    muscle_id     BIGINT           NOT NULL REFERENCES muscles (id),
    role          VARCHAR(50)      NOT NULL,
    impact_factor DOUBLE PRECISION NOT NULL DEFAULT 1.0
);

-- Collection Tables
CREATE TABLE movement_equipment
(
    movement_id VARCHAR(20) NOT NULL REFERENCES movements (id),
    equipment   VARCHAR(50) NOT NULL
);

CREATE TABLE movement_variations
(
    movement_id VARCHAR(20) NOT NULL REFERENCES movements (id),
    technique   VARCHAR(50) NOT NULL
);

-- ----------------------------------------------------------------------------------
-- 2. WORKOUT DEFINITIONS (WODs)
-- ----------------------------------------------------------------------------------

CREATE TABLE wods
(
    id               BIGSERIAL PRIMARY KEY,

    title            VARCHAR(100) NOT NULL,
    wod_type         VARCHAR(50)  NOT NULL,
    score_type       VARCHAR(20)  NOT NULL,
    creator_id       BIGINT,
    is_public        BOOLEAN      NOT NULL DEFAULT FALSE,

    description      TEXT,
    notes            TEXT,

    time_cap_seconds INTEGER,
    emom_interval    INTEGER,
    emom_rounds      INTEGER,
    rep_scheme       VARCHAR(100),

    created_at       TIMESTAMP    NOT NULL,
    updated_at       TIMESTAMP    NOT NULL
);

-- Indexes defined in Wod.java
CREATE INDEX idx_wod_title ON wods (title);
CREATE INDEX idx_wod_type ON wods (wod_type);

CREATE TABLE wod_modalities
(
    wod_id   BIGINT      NOT NULL REFERENCES wods (id),
    modality VARCHAR(50) NOT NULL
);

CREATE TABLE wod_movements
(
    id              BIGSERIAL PRIMARY KEY,
    wod_id          BIGINT      NOT NULL REFERENCES wods (id) ON DELETE CASCADE,
    movement_id     VARCHAR(20) NOT NULL REFERENCES movements (id),
    order_index     INTEGER     NOT NULL,

    reps_scheme     VARCHAR(50),

    weight          DOUBLE PRECISION,
    weight_unit     VARCHAR(10),

    duration        INTEGER, -- Stored in Seconds
    duration_unit   VARCHAR(10),

    distance        DOUBLE PRECISION,
    distance_unit   VARCHAR(10),

    calories        INTEGER,

    notes           TEXT,
    scaling_options TEXT
);

-- ----------------------------------------------------------------------------------
-- 3. SCORES & LOGS (The Result)
-- ----------------------------------------------------------------------------------

CREATE TABLE wod_scores
(
    id                 BIGSERIAL PRIMARY KEY,
    user_id            BIGINT      NOT NULL,
    date               DATE        NOT NULL,
    wod_id             BIGINT      NOT NULL REFERENCES wods (id),

    -- Metrics
    time_seconds       INTEGER,
    time_display_unit  VARCHAR(10),

    rounds             INTEGER,
    reps               INTEGER,

    max_weight         DOUBLE PRECISION,
    total_load         DOUBLE PRECISION,
    weight_unit        VARCHAR(10),

    total_distance     DOUBLE PRECISION,
    distance_unit      VARCHAR(10),

    total_calories     INTEGER,

    -- Metadata
    is_personal_record BOOLEAN     NOT NULL DEFAULT FALSE,
    time_capped        BOOLEAN     NOT NULL DEFAULT FALSE,
    scaling            VARCHAR(20) NOT NULL,
    scaling_notes      TEXT,
    user_comment       TEXT,

    logged_at          TIMESTAMP   NOT NULL
);

-- Indexes defined in WodScore.java
CREATE INDEX idx_user_date ON wod_scores (user_id, date);
CREATE INDEX idx_wod_user ON wod_scores (wod_id, user_id);
CREATE INDEX idx_user_pr ON wod_scores (user_id, is_personal_record);

-- ----------------------------------------------------------------------------------
-- 4. PERSONAL RECORDS (Read Model)
-- ----------------------------------------------------------------------------------

CREATE TABLE personal_records
(
    id                    BIGSERIAL PRIMARY KEY,
    user_id               BIGINT    NOT NULL,

    source_performance_id BIGINT    NOT NULL REFERENCES wod_scores (id) ON DELETE CASCADE,

    movement_id           VARCHAR(20) REFERENCES movements (id),
    wod_id                BIGINT REFERENCES wods (id),

    achieved_date         DATE,

    weight                DOUBLE PRECISION,
    reps                  INTEGER,
    time_seconds          INTEGER,

    workout_time_seconds  INTEGER,
    rounds                INTEGER,
    total_reps            INTEGER,

    notes                 TEXT,
    is_current_pr         BOOLEAN   NOT NULL DEFAULT TRUE,
    created_at            TIMESTAMP NOT NULL
);

-- Indexes defined in PersonalRecord.java
CREATE INDEX idx_pr_user_movement ON personal_records (user_id, movement_id);
CREATE INDEX idx_pr_user_wod ON personal_records (user_id, wod_id);