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
-- 1. MUSCLE & MOVEMENT LIBRARY
-- ----------------------------------------------------------------------------------

CREATE TABLE muscles
(
    -- Identification
    id             BIGSERIAL PRIMARY KEY,
    medical_name   VARCHAR(100) NOT NULL UNIQUE,
    -- Characteristics
    muscle_group   VARCHAR(50)  NOT NULL,
    -- Internationalized Content
    common_name_en VARCHAR(100),
    common_name_fr VARCHAR(100),
    description_en TEXT,
    description_fr TEXT,
    -- Media
    image_url      VARCHAR(512),
    -- Audit
    created_at     TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE movements
(
    -- Identification
    id                BIGSERIAL PRIMARY KEY,
    name              VARCHAR(50) NOT NULL,
    name_abbreviation VARCHAR(20),
    category          VARCHAR(30) NOT NULL,
    -- Internationalized Content
    description_en    TEXT,
    description_fr    TEXT,
    coaching_cues_en  TEXT,
    coaching_cues_fr  TEXT,
    -- Media
    video_url         VARCHAR(512),
    image_url         VARCHAR(512),
    -- Audit
    created_at        TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP   NOT NULL DEFAULT NOW()
);

-- Collection Tables
CREATE TABLE movement_equipment
(
    movement_id BIGINT      NOT NULL REFERENCES movements (id) ON DELETE CASCADE,
    equipment   VARCHAR(50) NOT NULL
);

CREATE TABLE movement_techniques
(
    movement_id BIGINT      NOT NULL REFERENCES movements (id) ON DELETE CASCADE,
    technique   VARCHAR(50) NOT NULL
);

-- Relationships Table
CREATE TABLE movement_muscles
(
    -- Identification
    id            BIGSERIAL PRIMARY KEY,
    -- Relationships
    movement_id   BIGINT           NOT NULL REFERENCES movements (id) ON DELETE CASCADE,
    muscle_id     BIGINT           NOT NULL REFERENCES muscles (id) ON DELETE CASCADE,
    role          VARCHAR(50)      NOT NULL,
    impact_factor DOUBLE PRECISION NOT NULL DEFAULT 1.0,

    CONSTRAINT check_impact_factor_range CHECK (impact_factor >= 0 AND impact_factor <= 1)
);

-- ----------------------------------------------------------------------------------
-- 2. WOD
-- ----------------------------------------------------------------------------------

CREATE TABLE wods
(
    -- Identification
    id               BIGSERIAL PRIMARY KEY,
    version          BIGINT,
    title            VARCHAR(100) NOT NULL,
    wod_type         VARCHAR(50)  NOT NULL,
    score_type       VARCHAR(20)  NOT NULL,
    -- Authorship and Visibility
    author_id        BIGINT,
    gym_id           BIGINT,
    is_public        BOOLEAN      NOT NULL DEFAULT FALSE,
    -- Description and Notes
    description      TEXT,
    notes            TEXT,
    -- Structure and Prescription
    time_cap_seconds INTEGER,
    emom_interval    INTEGER,
    emom_rounds      INTEGER,
    rep_scheme       VARCHAR(100),
    -- Audit
    created_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- Collection Tables
CREATE TABLE wod_modalities
(
    wod_id   BIGINT      NOT NULL REFERENCES wods (id) ON DELETE CASCADE,
    modality VARCHAR(50) NOT NULL
);

-- Relationships Table
CREATE TABLE wod_movements
(
    -- Identification
    id                    BIGSERIAL PRIMARY KEY,
    wod_id                BIGINT  NOT NULL REFERENCES wods (id) ON DELETE CASCADE,
    movement_id           BIGINT  NOT NULL REFERENCES movements (id),
    order_index           INTEGER NOT NULL,
    -- Prescription
    reps_scheme           VARCHAR(50),
    weight                DOUBLE PRECISION,
    weight_unit           VARCHAR(10) DEFAULT 'KG',
    duration_seconds      INTEGER,
    duration_display_unit VARCHAR(10) DEFAULT 'SECONDS',
    distance              DOUBLE PRECISION,
    distance_unit         VARCHAR(10) DEFAULT 'METERS',
    calories              INTEGER,
    -- Instructions
    notes                 TEXT,
    scaling_options       TEXT,

    CONSTRAINT check_order_positive CHECK (order_index > 0),
    CONSTRAINT uk_wod_movement_order UNIQUE (wod_id, order_index)
);

-- Collection Tables
CREATE TABLE wod_movement_equipment
(
    wod_movement_id BIGINT      NOT NULL REFERENCES wod_movements (id) ON DELETE CASCADE,
    equipment       VARCHAR(50) NOT NULL
);

CREATE TABLE wod_movement_techniques
(
    wod_movement_id BIGINT      NOT NULL REFERENCES wod_movements (id) ON DELETE CASCADE,
    technique       VARCHAR(50) NOT NULL
);

-- ----------------------------------------------------------------------------------
-- 3. SCORES
-- ----------------------------------------------------------------------------------

CREATE TABLE scores
(
    -- Identification
    id                    BIGSERIAL PRIMARY KEY,
    user_id               BIGINT      NOT NULL,
    date                  DATE        NOT NULL,
    wod_id                BIGINT      NOT NULL REFERENCES wods (id) ON DELETE CASCADE,
    -- Time Metrics
    time_seconds          INTEGER,
    time_display_unit     VARCHAR(10)          DEFAULT 'SECONDS',
    -- Volume Metrics
    rounds                INTEGER,
    reps                  INTEGER,
    -- Load Metrics
    max_weight_kg         DOUBLE PRECISION,
    total_load_kg         DOUBLE PRECISION,
    weight_display_unit   VARCHAR(10)          DEFAULT 'KG',
    -- Distance Metrics
    total_distance_meters DOUBLE PRECISION,
    distance_display_unit VARCHAR(10)          DEFAULT 'METERS',
    -- Calories
    total_calories        INTEGER,
    -- Performance Context
    scaling               VARCHAR(20) NOT NULL,
    time_capped           BOOLEAN     NOT NULL DEFAULT FALSE,
    is_personal_record    BOOLEAN     NOT NULL DEFAULT FALSE,
    -- Comments and Scaling notes
    scaling_notes         TEXT,
    user_comment          TEXT,
    -- Audit
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
-- MOVEMENT_MUSCLES
-- ----------------------------------------------------------------------------------
CREATE INDEX idx_movement_muscles_movement ON movement_muscles (movement_id);
CREATE INDEX idx_movement_muscles_muscle ON movement_muscles (muscle_id);
CREATE INDEX idx_movement_muscles_role ON movement_muscles (role, impact_factor);

-- ----------------------------------------------------------------------------------
-- MOVEMENTS
-- ----------------------------------------------------------------------------------
CREATE INDEX idx_movements_category ON movements (category);
-- noinspection SqlResolve
CREATE INDEX idx_movements_name_trgm ON movements USING gin (name gin_trgm_ops);

-- ----------------------------------------------------------------------------------
-- WODS
-- ----------------------------------------------------------------------------------
CREATE INDEX idx_wods_title ON wods (title);
CREATE INDEX idx_wods_wod_type ON wods (wod_type);
CREATE INDEX idx_wods_gym_id ON wods (gym_id);
CREATE INDEX idx_wods_author_id ON wods (author_id);
-- ----------------------------------------------------------------------------------
-- WODS_MOVEMENTS
-- ----------------------------------------------------------------------------------
CREATE INDEX idx_wod_movement_eq ON wod_movement_equipment (wod_movement_id);
CREATE INDEX idx_wod_movement_tech ON wod_movement_techniques (wod_movement_id);

-- ----------------------------------------------------------------------------------
-- SCORES
-- ----------------------------------------------------------------------------------
CREATE INDEX idx_user_date ON scores (user_id, date DESC);
CREATE INDEX idx_wod_user ON scores (wod_id, user_id);
CREATE INDEX idx_user_pr ON scores (user_id) WHERE is_personal_record = TRUE;
CREATE INDEX idx_scores_wod_scaling ON scores (wod_id, scaling, time_seconds);

-- ==================================================================================
-- COMMENTS
-- ==================================================================================

COMMENT ON TABLE movements IS 'Master catalog of exercises/movements';

COMMENT ON TABLE movement_muscles IS 'Weighted relationship between movements and muscles';
COMMENT ON COLUMN movement_muscles.role IS 'AGONIST, SYNERGIST, or STABILIZER';
COMMENT ON COLUMN movement_muscles.impact_factor IS 'Activation coefficient (0.0 to 1.0)';

COMMENT ON TABLE wods IS 'Workout definitions (the recipe)';
COMMENT ON COLUMN wods.author_id IS 'User ID who created and authored the content. Null means System.';

COMMENT ON TABLE scores IS 'Athlete performance results (the execution)';
COMMENT ON COLUMN scores.time_display_unit IS 'User preference for display';
COMMENT ON COLUMN scores.time_seconds IS 'Canonical storage in seconds';
COMMENT ON COLUMN scores.max_weight_kg IS 'Canonical storage in Kilograms';
COMMENT ON COLUMN scores.total_distance_meters IS 'Canonical storage in Meters';