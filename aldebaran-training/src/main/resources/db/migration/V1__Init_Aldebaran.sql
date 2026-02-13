-- ==================================================================================
-- V1__Init_Aldebaran.sql
-- Initial schema for Aldebaran Training Microservice (PostgreSQL)
-- Includes: Schema + Indexes + Constraints + Extensions
-- ==================================================================================

-- ----------------------------------------------------------------------------------
-- 0. EXTENSIONS
-- ----------------------------------------------------------------------------------
-- Recherche full-text fuzzy (Trigram similarity)
CREATE EXTENSION IF NOT EXISTS pg_trgm;

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
    muscle_group   VARCHAR(50)  NOT NULL,

    -- Audit
    created_at     TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE movements
(
    id                  VARCHAR(20) PRIMARY KEY, -- Business Key (e.g., WL-SQ-001)

    name                VARCHAR(50)      NOT NULL,
    name_abbreviation   VARCHAR(20),

    category            VARCHAR(30)      NOT NULL,

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
    created_at          TIMESTAMP        NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP        NOT NULL DEFAULT NOW()
);

-- Weighted Join Table for Anatomy
CREATE TABLE movement_muscles
(
    id            BIGSERIAL PRIMARY KEY,
    movement_id   VARCHAR(20)      NOT NULL REFERENCES movements (id) ON DELETE CASCADE,
    muscle_id     BIGINT           NOT NULL REFERENCES muscles (id) ON DELETE CASCADE,
    role          VARCHAR(50)      NOT NULL,
    impact_factor DOUBLE PRECISION NOT NULL DEFAULT 1.0,

    CONSTRAINT check_impact_factor_range CHECK (impact_factor >= 0 AND impact_factor <= 1)
);

-- Collection Tables
CREATE TABLE movement_equipment
(
    movement_id VARCHAR(20) NOT NULL REFERENCES movements (id) ON DELETE CASCADE,
    equipment   VARCHAR(50) NOT NULL
);

CREATE TABLE movement_variations
(
    movement_id VARCHAR(20) NOT NULL REFERENCES movements (id) ON DELETE CASCADE,
    technique   VARCHAR(50) NOT NULL
);

-- Constraint: Bodyweight consistency
ALTER TABLE movements
    ADD CONSTRAINT check_bodyweight_consistency
        CHECK (
            (involves_bodyweight = TRUE AND bodyweight_factor > 0) OR
            (involves_bodyweight = FALSE AND bodyweight_factor = 0)
            );

-- ----------------------------------------------------------------------------------
-- 2. WORKOUT DEFINITIONS (WODs)
-- ----------------------------------------------------------------------------------

CREATE TABLE wods
(
    id               BIGSERIAL PRIMARY KEY,
    version          BIGINT,

    title            VARCHAR(100) NOT NULL,
    wod_type         VARCHAR(50)  NOT NULL,
    score_type       VARCHAR(20)  NOT NULL,
    author_id        BIGINT,
    gym_id           BIGINT,
    is_public        BOOLEAN      NOT NULL DEFAULT FALSE,

    description      TEXT,
    notes            TEXT,

    time_cap_seconds INTEGER,
    emom_interval    INTEGER,
    emom_rounds      INTEGER,
    rep_scheme       VARCHAR(100),

    created_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE wod_modalities
(
    wod_id   BIGINT      NOT NULL REFERENCES wods (id) ON DELETE CASCADE,
    modality VARCHAR(50) NOT NULL
);

CREATE TABLE wod_movements
(
    id                    BIGSERIAL PRIMARY KEY,
    wod_id                BIGINT      NOT NULL REFERENCES wods (id) ON DELETE CASCADE,
    movement_id           VARCHAR(20) NOT NULL REFERENCES movements (id),
    order_index           INTEGER     NOT NULL,

    reps_scheme           VARCHAR(50),

    weight                DOUBLE PRECISION,
    weight_unit           VARCHAR(10) DEFAULT 'KG',

    duration_seconds      INTEGER,
    duration_display_unit VARCHAR(10) DEFAULT 'SECONDS',

    distance              DOUBLE PRECISION,
    distance_unit         VARCHAR(10) DEFAULT 'METERS',

    calories              INTEGER,

    notes                 TEXT,
    scaling_options       TEXT,

    CONSTRAINT check_order_positive CHECK (order_index > 0)
);

-- ----------------------------------------------------------------------------------
-- 3. SCORES & LOGS (The Result)
-- ----------------------------------------------------------------------------------

CREATE TABLE wod_scores
(
    id                    BIGSERIAL PRIMARY KEY,
    user_id               BIGINT      NOT NULL,
    date                  DATE        NOT NULL,
    wod_id                BIGINT      NOT NULL REFERENCES wods (id) ON DELETE CASCADE,

    -- Metrics (Canonical Storage)
    time_seconds          INTEGER,
    time_display_unit     VARCHAR(10)          DEFAULT 'SECONDS',

    rounds                INTEGER,
    reps                  INTEGER,

    -- Normalized Weight (KG)
    max_weight_kg         DOUBLE PRECISION,
    total_load_kg         DOUBLE PRECISION,
    weight_display_unit   VARCHAR(10)          DEFAULT 'KG',     -- User preference for display

    -- Normalized Distance (Meters)
    total_distance_meters DOUBLE PRECISION,
    distance_display_unit VARCHAR(10)          DEFAULT 'METERS', -- User preference for display

    total_calories        INTEGER,

    -- Metadata
    is_personal_record    BOOLEAN     NOT NULL DEFAULT FALSE,
    time_capped           BOOLEAN     NOT NULL DEFAULT FALSE,
    scaling               VARCHAR(20) NOT NULL,
    scaling_notes         TEXT,
    user_comment          TEXT,

    logged_at             TIMESTAMP   NOT NULL DEFAULT NOW(),

    -- Constraints
    CONSTRAINT check_time_positive CHECK (time_seconds IS NULL OR time_seconds > 0),
    CONSTRAINT check_rounds_positive CHECK (rounds IS NULL OR rounds >= 0),
    CONSTRAINT check_weight_positive CHECK (max_weight_kg IS NULL OR max_weight_kg > 0)
);

-- ==================================================================================
-- INDEXES - Performance Optimization
-- ==================================================================================

-- ----------------------------------------------------------------------------------
-- MOVEMENT_MUSCLES (Anatomical queries)
-- ----------------------------------------------------------------------------------
CREATE INDEX idx_movement_muscles_movement ON movement_muscles (movement_id);
CREATE INDEX idx_movement_muscles_muscle ON movement_muscles (muscle_id);
CREATE INDEX idx_movement_muscles_role ON movement_muscles (role, impact_factor);

-- ----------------------------------------------------------------------------------
-- WOD_MOVEMENTS (WOD structure queries)
-- ----------------------------------------------------------------------------------
CREATE INDEX idx_wod_movements_wod_order ON wod_movements (wod_id, order_index);

-- ----------------------------------------------------------------------------------
-- MOVEMENTS (Search & Filtering)
-- ----------------------------------------------------------------------------------
CREATE INDEX idx_movements_category ON movements (category);
CREATE INDEX idx_movements_name_trgm ON movements USING gin (name gin_trgm_ops);

-- ----------------------------------------------------------------------------------
-- WODS (Search)
-- ----------------------------------------------------------------------------------
CREATE INDEX idx_wod_title ON wods (title);
CREATE INDEX idx_wod_type ON wods (wod_type);
CREATE INDEX idx_wods_gym_id ON wods (gym_id);
CREATE INDEX idx_wods_author_id ON wods (author_id);

-- ----------------------------------------------------------------------------------
-- WOD_SCORES (Leaderboards & History)
-- ----------------------------------------------------------------------------------
CREATE INDEX idx_user_date ON wod_scores (user_id, date DESC);
CREATE INDEX idx_wod_user ON wod_scores (wod_id, user_id);
CREATE INDEX idx_user_pr ON wod_scores (user_id) WHERE is_personal_record = TRUE;
CREATE INDEX idx_wod_scores_wod_scaling ON wod_scores (wod_id, scaling, time_seconds);

-- ==================================================================================
-- COMMENTS (Documentation in database)
-- ==================================================================================

COMMENT ON TABLE movements IS 'Master catalog of exercises/movements';
COMMENT ON COLUMN movements.id IS 'Business key format: {MODALITY}-{FAMILY}-{SEQUENCE} (e.g., WL-SQ-001)';
COMMENT ON COLUMN movements.bodyweight_factor IS 'Percentage of bodyweight involved (0.0 to 1.0)';

COMMENT ON TABLE movement_muscles IS 'Weighted relationship between movements and muscles';
COMMENT ON COLUMN movement_muscles.role IS 'AGONIST, SYNERGIST, or STABILIZER';
COMMENT ON COLUMN movement_muscles.impact_factor IS 'Activation coefficient (0.0 to 1.0)';

COMMENT ON TABLE wods IS 'Workout definitions (the recipe)';
COMMENT ON COLUMN wods.author_id IS 'User ID who created and authored the content. Null means System.';

COMMENT ON TABLE wod_scores IS 'Athlete performance results (the execution)';
COMMENT ON COLUMN wod_scores.time_display_unit IS 'User preference for display';
COMMENT ON COLUMN wod_scores.time_seconds IS 'Canonical storage in seconds';
COMMENT ON COLUMN wod_scores.max_weight_kg IS 'Canonical storage in Kilograms';
COMMENT ON COLUMN wod_scores.total_distance_meters IS 'Canonical storage in Meters';