-- Weekly balance of an athlete (lot D, D120): totals per athlete and per week (key = Monday),
-- computed on first display from scores and journals, never from a request of someone else.
-- A write only marks the week stale; reading a stale week, a week of an older rules version or a
-- missing week rebuilds it in full.

CREATE TABLE balance_weeks
(
    user_id           BIGINT           NOT NULL,
    week_start        DATE             NOT NULL,
    rules_version     INTEGER          NOT NULL DEFAULT 0,
    stale             BOOLEAN          NOT NULL DEFAULT TRUE,
    -- Equivalent sets of the week, all lines (volume histogram)
    total_sets        DOUBLE PRECISION NOT NULL DEFAULT 0,
    -- Equivalent sets per modality (D127)
    weightlifting     DOUBLE PRECISION NOT NULL DEFAULT 0,
    gymnastics        DOUBLE PRECISION NOT NULL DEFAULT 0,
    monostructural    DOUBLE PRECISION NOT NULL DEFAULT 0,
    -- Metcon seconds per time domain (D86)
    very_short_s      INTEGER          NOT NULL DEFAULT 0,
    short_s           INTEGER          NOT NULL DEFAULT 0,
    medium_s          INTEGER          NOT NULL DEFAULT 0,
    long_s            INTEGER          NOT NULL DEFAULT 0,
    intervals_s       INTEGER          NOT NULL DEFAULT 0,
    computed_at       TIMESTAMP,
    PRIMARY KEY (user_id, week_start),
    CONSTRAINT chk_balance_week_monday CHECK (EXTRACT(ISODOW FROM week_start) = 1)
);

-- Sets per exact muscle: moving (agonist 1, synergist 0.5) and holding (stabilizer 1)
CREATE TABLE balance_week_muscles
(
    user_id      BIGINT           NOT NULL,
    week_start   DATE             NOT NULL,
    muscle_id    BIGINT           NOT NULL REFERENCES muscles (id) ON DELETE CASCADE,
    moving_sets  DOUBLE PRECISION NOT NULL,
    holding_sets DOUBLE PRECISION NOT NULL,
    PRIMARY KEY (user_id, week_start, muscle_id),
    FOREIGN KEY (user_id, week_start) REFERENCES balance_weeks (user_id, week_start) ON DELETE CASCADE
);

-- Strength sets per lift (base movement, or a complex under its name) and "near the record" (D124)
CREATE TABLE balance_week_lifts
(
    user_id     BIGINT           NOT NULL,
    week_start  DATE             NOT NULL,
    label       VARCHAR(200)     NOT NULL,
    movement_id BIGINT REFERENCES movements (id) ON DELETE CASCADE,
    sets        DOUBLE PRECISION NOT NULL,
    near_record BOOLEAN          NOT NULL DEFAULT FALSE,
    PRIMARY KEY (user_id, week_start, label),
    FOREIGN KEY (user_id, week_start) REFERENCES balance_weeks (user_id, week_start) ON DELETE CASCADE
);
