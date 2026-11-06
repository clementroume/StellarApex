-- V1__Baseline.sql
-- Condensed baseline of the Aldebaran schema (former V1-V19). One-off exception to "never edit an
-- applied migration" (roadmap D67): there is no real database yet, so the history was folded into one
-- script before the local wipe. It is meant to be condensed once more at the end of the roadmap,
-- before the first production release; after that, migrations are never edited again.
-- The former migrations only converted rows that no longer exist (WOD categories and structures,
-- `reps_per_round`, `rep_max` deduced from "N RM" schemes, score gym / author / session block...):
-- the baseline creates empty tables, and the catalog (muscles, movements, public WODs with their
-- `rep_max`) is reloaded from the CSV files by DataSeeder.
-- Column order follows the order in which the former migrations added the columns.

-- Fuzzy search (trigram similarity)
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- ----------------------------------------------------------------------------------
-- 1. MUSCLE & MOVEMENT LIBRARY
-- ----------------------------------------------------------------------------------

CREATE TABLE muscles
(
    id             BIGSERIAL PRIMARY KEY,
    medical_name   VARCHAR(100) NOT NULL UNIQUE,
    muscle_group   VARCHAR(50)  NOT NULL,
    common_name_en VARCHAR(100),
    common_name_fr VARCHAR(100),
    description_en TEXT,
    description_fr TEXT,
    image_url      VARCHAR(512),
    created_at     TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMP    NOT NULL DEFAULT NOW(),
    -- Functional group (e.g. Rectus Femoris -> Quadriceps Femoris). One level only, enforced by
    -- MuscleService. Deleting a group keeps its muscles.
    parent_id      BIGINT REFERENCES muscles (id) ON DELETE SET NULL
);

CREATE TABLE movements
(
    id                BIGSERIAL PRIMARY KEY,
    name              VARCHAR(50) NOT NULL,
    name_abbreviation VARCHAR(20),
    category          VARCHAR(30) NOT NULL,
    description_en    TEXT,
    description_fr    TEXT,
    coaching_cues_en  TEXT,
    coaching_cues_fr  TEXT,
    video_url         VARCHAR(512),
    image_url         VARCHAR(512),
    created_at        TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP   NOT NULL DEFAULT NOW()
);

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

CREATE TABLE movement_muscles
(
    id            BIGSERIAL PRIMARY KEY,
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
    id                 BIGSERIAL PRIMARY KEY,
    version            BIGINT,
    title              VARCHAR(100) NOT NULL,
    -- Category of format (FOR_TIME, AMRAP, EMOM, TABATA, FOR_LOAD, FOR_QUALITY) and its sub-category
    -- `structure` guiding the entry
    wod_type           VARCHAR(50)  NOT NULL,
    score_type         VARCHAR(20)  NOT NULL,
    author_id          BIGINT,
    gym_id             BIGINT,
    is_public          BOOLEAN      NOT NULL DEFAULT FALSE,
    description_en     TEXT,
    notes_en           TEXT,
    time_cap_seconds   INTEGER,
    emom_interval      INTEGER,
    emom_rounds        INTEGER,
    rep_scheme         VARCHAR(100),
    created_at         TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMP    NOT NULL DEFAULT NOW(),
    -- Optional category (GIRLS, HERO, OPEN...): Fran is a For Time workout of the Girls category
    category           VARCHAR(50),
    description_fr     TEXT,
    notes_fr           TEXT,
    -- Official tiebreak of a WOD (how the time is taken, EN / FR); NULL when the WOD has none
    tiebreak_en        TEXT,
    tiebreak_fr        TEXT,
    -- Reps of one round of an AMRAP with a fixed round (Cindy: 5 + 10 + 15 = 30); NULL otherwise
    reps_per_round     INTEGER,
    -- Competition WODs: gym WODs on which staff holding SCORE_VERIFY log scores for athletes and
    -- verify them (judging). A copied WOD keeps a link to the WOD it was copied from.
    competition        BOOLEAN      NOT NULL DEFAULT FALSE,
    source_wod_id      BIGINT REFERENCES wods (id) ON DELETE SET NULL,
    -- Session WODs (standalone = false) are hidden from WOD lists and edited through their session
    standalone         BOOLEAN      NOT NULL DEFAULT TRUE,
    -- Structured prescription: rounds of a "rounds for time", rest between rounds, work time of
    -- intervals, ascending ladder (start, step), weight vest
    rounds             INTEGER CHECK (rounds >= 1),
    rest_seconds       INTEGER CHECK (rest_seconds >= 0),
    work_seconds       INTEGER CHECK (work_seconds >= 1),
    ladder_start       INTEGER CHECK (ladder_start >= 1),
    ladder_step        INTEGER CHECK (ladder_step >= 1),
    vest_weight        DOUBLE PRECISION CHECK (vest_weight >= 0),
    vest_weight_female DOUBLE PRECISION CHECK (vest_weight_female >= 0),
    vest_weight_unit   VARCHAR(10),
    structure          VARCHAR(20),
    -- Team WODs: team size and how the work is shared
    team_size          INTEGER CHECK (team_size BETWEEN 2 AND 6),
    team_mode          VARCHAR(20) CHECK (team_mode IN ('YOU_GO_I_GO', 'SPLIT', 'SYNCHRO')),
    -- N of an "N RM" test (For Load WOD with one movement): its score counts as an N-rep record
    rep_max            INTEGER CHECK (rep_max BETWEEN 1 AND 50),

    CONSTRAINT check_reps_per_round_positive CHECK (reps_per_round IS NULL OR reps_per_round > 0)
);

CREATE TABLE wod_modalities
(
    wod_id   BIGINT      NOT NULL REFERENCES wods (id) ON DELETE CASCADE,
    modality VARCHAR(50) NOT NULL
);

CREATE TABLE wod_movements
(
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
    distance_unit         VARCHAR(20) DEFAULT 'METERS',
    calories              INTEGER,
    -- Instructions (FR / EN)
    notes_en              TEXT,
    scaling_options_en    TEXT,
    notes_fr              TEXT,
    scaling_options_fr    TEXT,
    -- Women's values next to the men's ones ("@43/30 kg", "14/12 cal", "60/50 cm")
    weight_female         DOUBLE PRECISION
        CONSTRAINT check_weight_female_positive CHECK (weight_female >= 0),
    calories_female       INTEGER
        CONSTRAINT check_calories_female_positive CHECK (calories_female >= 0),
    height                DOUBLE PRECISION
        CONSTRAINT check_height_positive CHECK (height >= 0),
    height_female         DOUBLE PRECISION
        CONSTRAINT check_height_female_positive CHECK (height_female >= 0),
    height_unit           VARCHAR(20),
    -- Prescribed number of sets of a movement (e.g. 5 with reps_scheme "5-5-3-3-1")
    sets                  INTEGER CHECK (sets >= 1),
    -- Buy-in, the rounds (MAIN) or the buy-out
    segment               VARCHAR(10) NOT NULL DEFAULT 'MAIN'
        CHECK (segment IN ('BUY_IN', 'MAIN', 'BUY_OUT')),
    -- Lines of a weightlifting complex done as one set (1 snatch pull + 1 snatch + 1 overhead squat)
    complex_group         INTEGER CHECK (complex_group >= 1),
    -- Team WODs: the movement is done in total, by each member, or together
    team_split            VARCHAR(10) NOT NULL DEFAULT 'TOTAL'
        CHECK (team_split IN ('TOTAL', 'EACH', 'TOGETHER')),
    -- Load prescribed as a percentage of the athlete's N RM of the movement (base version)
    percent_of_rm         DOUBLE PRECISION CHECK (percent_of_rm > 0 AND percent_of_rm <= 150),
    rm_reps               INTEGER CHECK (rm_reps BETWEEN 1 AND 50),

    CONSTRAINT check_order_positive CHECK (order_index > 0),
    CONSTRAINT uk_wod_movement_order UNIQUE (wod_id, order_index)
);

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
-- 3. SESSIONS
-- ----------------------------------------------------------------------------------

-- A session is an ordered container of blocks proposed on a day (WOD session) or a week (specialty
-- session, date = Monday). A scored block points at a WOD, either created for the session
-- (standalone = false) or an existing standalone WOD (borrowed). Warm-up / cool-down blocks are
-- FR/EN text only.
CREATE TABLE sessions
(
    id                   BIGSERIAL PRIMARY KEY,
    version              BIGINT      NOT NULL DEFAULT 0,
    session_type         VARCHAR(20) NOT NULL CHECK (session_type IN ('WOD', 'SPECIALTY')),
    discipline           VARCHAR(20) NOT NULL,
    -- Day of a WOD session, Monday of the week of a specialty session
    date                 DATE        NOT NULL,
    -- Last day the session is proposed on (= date for a WOD session, date + 6 for a specialty)
    end_date             DATE        NOT NULL,
    title                VARCHAR(100),
    description_en       TEXT,
    description_fr       TEXT,
    gym_id               BIGINT,
    author_id            BIGINT      NOT NULL,
    -- Read rule of the content for the box's athletes (null for a personal session)
    visibility_policy    VARCHAR(30),
    -- Instant the content opens to the whole box (null: participants only, or personal session)
    content_visible_from TIMESTAMPTZ,
    created_at           TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMP   NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_sessions_dates CHECK (end_date >= date)
);

CREATE TABLE session_blocks
(
    id              BIGSERIAL PRIMARY KEY,
    session_id      BIGINT      NOT NULL REFERENCES sessions (id) ON DELETE CASCADE,
    order_index     INTEGER     NOT NULL,
    kind            VARCHAR(20) NOT NULL,
    label           VARCHAR(30),
    -- Thematic summary shown in advance ("Snatch lourd EMOM 3/4"), optional
    theme_en        VARCHAR(80),
    theme_fr        VARCHAR(80),
    -- Offered on top of the session (accessory, finisher...)
    optional        BOOLEAN     NOT NULL DEFAULT FALSE,
    stimulus        VARCHAR(20),
    planned_minutes INTEGER CHECK (planned_minutes >= 0),
    text_en         TEXT,
    text_fr         TEXT,
    wod_id          BIGINT REFERENCES wods (id) ON DELETE RESTRICT,
    -- Deferred: reordering blocks swaps indexes within one transaction
    CONSTRAINT uk_session_block_order UNIQUE (session_id, order_index) DEFERRABLE INITIALLY DEFERRED
);

-- Session settings of a box: default visibility policy and the time zone used to compute
-- "the day of the session at 18:00" or "the next day".
CREATE TABLE gym_session_settings
(
    gym_id             BIGINT PRIMARY KEY,
    default_visibility VARCHAR(30) NOT NULL,
    time_zone          VARCHAR(50) NOT NULL DEFAULT 'Europe/Paris'
);

-- Journal of the sessions an athlete did ("Séance faite"), one per athlete and session, with the
-- blocks done; source PLANNING is reserved for the future planning service (attendance).
CREATE TABLE session_logs
(
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT      NOT NULL,
    session_id BIGINT      NOT NULL REFERENCES sessions (id) ON DELETE CASCADE,
    gym_id     BIGINT,
    date       DATE        NOT NULL,
    source     VARCHAR(20) NOT NULL CHECK (source IN ('MANUAL', 'PLANNING')),
    logged_by  BIGINT      NOT NULL,
    created_at TIMESTAMP   NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_session_log_user UNIQUE (user_id, session_id)
);

CREATE TABLE session_log_blocks
(
    log_id   BIGINT  NOT NULL REFERENCES session_logs (id) ON DELETE CASCADE,
    block_id BIGINT  NOT NULL REFERENCES session_blocks (id) ON DELETE CASCADE,
    done     BOOLEAN NOT NULL,
    PRIMARY KEY (log_id, block_id)
);

-- ----------------------------------------------------------------------------------
-- 4. SCORES
-- ----------------------------------------------------------------------------------

-- Team scores: one performance shared by its members; each member gets the same score row
-- (records, journal, history) linked to the team; leaderboards show one row per team.
CREATE TABLE score_teams
(
    id         BIGSERIAL PRIMARY KEY,
    wod_id     BIGINT    NOT NULL REFERENCES wods (id) ON DELETE CASCADE,
    gym_id     BIGINT    NOT NULL,
    created_by BIGINT    NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE score_team_members
(
    team_id BIGINT NOT NULL REFERENCES score_teams (id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL,
    PRIMARY KEY (team_id, user_id)
);

CREATE TABLE scores
(
    id                     BIGSERIAL PRIMARY KEY,
    user_id                BIGINT      NOT NULL,
    date                   DATE        NOT NULL,
    wod_id                 BIGINT      NOT NULL REFERENCES wods (id) ON DELETE CASCADE,
    -- Time Metrics
    time_seconds           INTEGER,
    time_display_unit      VARCHAR(10)          DEFAULT 'SECONDS',
    -- Volume Metrics
    rounds                 INTEGER,
    reps                   INTEGER,
    -- Load Metrics
    max_weight_kg          DOUBLE PRECISION,
    total_load_kg          DOUBLE PRECISION,
    weight_display_unit    VARCHAR(10)          DEFAULT 'KG',
    -- Distance Metrics
    total_distance_meters  DOUBLE PRECISION,
    distance_display_unit  VARCHAR(20)          DEFAULT 'METERS',
    -- Calories
    total_calories         INTEGER,
    -- Performance Context
    scaling                VARCHAR(20) NOT NULL,
    time_capped            BOOLEAN     NOT NULL DEFAULT FALSE,
    is_personal_record     BOOLEAN     NOT NULL DEFAULT FALSE,
    -- Comments and Scaling notes
    scaling_notes          TEXT,
    user_comment           TEXT,
    -- Audit
    logged_at              TIMESTAMP   NOT NULL DEFAULT NOW(),
    -- Optional tiebreak time of a score (elapsed seconds), only meaningful for WODs with a tiebreak
    tiebreak_seconds       INTEGER,
    -- Gym where a score was performed: the WOD's gym for a gym WOD, or one of the athlete's gyms
    -- (chosen when logging) for a public or personal WOD. Drives gym leaderboards and moderation.
    gym_id                 BIGINT,
    -- Who logged a score (the athlete, or the staff / admin logging it for them), and who verified it
    logged_by              BIGINT      NOT NULL,
    verified_by            BIGINT,
    verified_at            TIMESTAMP,
    -- Division of the athlete (MEN / WOMEN, MIXED for a team) when the score was logged, copied from
    -- the X-Auth-User-Division header (or from Antares for scores logged by staff), so leaderboards
    -- filter by division without calling Antares
    division               VARCHAR(10),
    -- A score done within a session is linked to the athlete's journal and to the block it was
    -- logged for (the lock of a block must not confuse two blocks borrowing the same WOD)
    session_log_id         BIGINT REFERENCES session_logs (id) ON DELETE RESTRICT,
    -- Competition WODs: an athlete chooses to submit a score for verification; staff only verify
    -- submitted scores
    verification_requested BOOLEAN     NOT NULL DEFAULT FALSE,
    team_id                BIGINT REFERENCES score_teams (id) ON DELETE SET NULL,
    session_block_id       BIGINT REFERENCES session_blocks (id) ON DELETE RESTRICT,
    -- Age group of the athlete in the year of the score (CrossFit Open brackets), copied like the
    -- division; the birth year itself never leaves Antares' storage
    age_group              VARCHAR(20),

    CONSTRAINT check_time_positive CHECK (time_seconds IS NULL OR time_seconds > 0),
    CONSTRAINT check_rounds_positive CHECK (rounds IS NULL OR rounds >= 0),
    CONSTRAINT check_weight_positive CHECK (max_weight_kg IS NULL OR max_weight_kg > 0),
    CONSTRAINT check_tiebreak_positive CHECK (tiebreak_seconds IS NULL OR tiebreak_seconds > 0),
    CONSTRAINT chk_scores_division CHECK (division IN ('MEN', 'WOMEN', 'MIXED'))
);

-- Strength sets, set by set (rising or falling load): the best complete set is the performance
CREATE TABLE score_sets
(
    score_id  BIGINT           NOT NULL REFERENCES scores (id) ON DELETE CASCADE,
    set_index INTEGER          NOT NULL,
    reps      INTEGER          NOT NULL CHECK (reps >= 0),
    weight_kg DOUBLE PRECISION CHECK (weight_kg >= 0),
    completed BOOLEAN          NOT NULL DEFAULT TRUE,
    PRIMARY KEY (score_id, set_index)
);

-- ----------------------------------------------------------------------------------
-- 5. RECORDS
-- ----------------------------------------------------------------------------------

-- Best load per athlete, movement, variant set and number of reps, rebuilt from the scores
CREATE TABLE movement_records
(
    id                 BIGSERIAL PRIMARY KEY,
    user_id            BIGINT           NOT NULL,
    movement_id        BIGINT           NOT NULL REFERENCES movements (id) ON DELETE CASCADE,
    -- Sorted equipment and techniques of the WOD line, implied equipment removed; '' = base version
    variant_key        VARCHAR(500)     NOT NULL DEFAULT '',
    reps               INTEGER          NOT NULL CHECK (reps >= 1),
    weight_kg          DOUBLE PRECISION NOT NULL CHECK (weight_kg >= 0),
    date               DATE             NOT NULL,
    score_id           BIGINT           NOT NULL REFERENCES scores (id) ON DELETE CASCADE,
    division           VARCHAR(10),
    age_group          VARCHAR(20),
    -- The load a record beat (best load before the record's day), for "+5 kg" on the records board
    previous_weight_kg DOUBLE PRECISION CHECK (previous_weight_kg >= 0),
    CONSTRAINT uk_movement_record UNIQUE (user_id, movement_id, variant_key, reps)
);

-- ==================================================================================
-- INDEXES
-- ==================================================================================

CREATE INDEX idx_muscles_parent_id ON muscles (parent_id);

CREATE INDEX idx_movement_muscles_movement ON movement_muscles (movement_id);
CREATE INDEX idx_movement_muscles_muscle ON movement_muscles (muscle_id);
CREATE INDEX idx_movement_muscles_role ON movement_muscles (role, impact_factor);

CREATE INDEX idx_movements_category ON movements (category);
-- noinspection SqlResolve
CREATE INDEX idx_movements_name_trgm ON movements USING gin (name gin_trgm_ops);

CREATE INDEX idx_wods_title ON wods (title);
CREATE INDEX idx_wods_wod_type ON wods (wod_type);
CREATE INDEX idx_wods_gym_id ON wods (gym_id);
CREATE INDEX idx_wods_author_id ON wods (author_id);
CREATE INDEX idx_wods_category ON wods (category);
CREATE INDEX idx_wods_standalone ON wods (standalone);

CREATE INDEX idx_wod_movement_eq ON wod_movement_equipment (wod_movement_id);
CREATE INDEX idx_wod_movement_tech ON wod_movement_techniques (wod_movement_id);

CREATE INDEX idx_sessions_gym_date ON sessions (gym_id, date);
CREATE INDEX idx_sessions_author ON sessions (author_id) WHERE gym_id IS NULL;
CREATE INDEX idx_session_blocks_wod ON session_blocks (wod_id);
CREATE INDEX idx_session_logs_user_date ON session_logs (user_id, date);

CREATE INDEX idx_user_date ON scores (user_id, date DESC);
CREATE INDEX idx_wod_user ON scores (wod_id, user_id);
CREATE INDEX idx_user_pr ON scores (user_id) WHERE is_personal_record = TRUE;
CREATE INDEX idx_scores_wod_scaling ON scores (wod_id, scaling, time_seconds);
CREATE INDEX idx_scores_wod_gym ON scores (wod_id, gym_id);
CREATE INDEX idx_scores_wod_division ON scores (wod_id, division);
CREATE INDEX idx_scores_session_log ON scores (session_log_id);
CREATE INDEX idx_scores_session_block ON scores (session_block_id);
CREATE INDEX idx_scores_team ON scores (team_id);

CREATE INDEX idx_movement_records_board ON movement_records (movement_id, variant_key, reps);
CREATE INDEX idx_movement_records_score ON movement_records (score_id);

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
