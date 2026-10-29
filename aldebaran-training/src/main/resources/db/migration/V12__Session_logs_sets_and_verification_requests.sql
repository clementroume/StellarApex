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

CREATE INDEX idx_session_logs_user_date ON session_logs (user_id, date);

CREATE TABLE session_log_blocks
(
    log_id   BIGINT  NOT NULL REFERENCES session_logs (id) ON DELETE CASCADE,
    block_id BIGINT  NOT NULL REFERENCES session_blocks (id) ON DELETE CASCADE,
    done     BOOLEAN NOT NULL,
    PRIMARY KEY (log_id, block_id)
);

-- Scores done within a session are linked to the athlete's journal of it
ALTER TABLE scores ADD COLUMN session_log_id BIGINT REFERENCES session_logs (id) ON DELETE RESTRICT;
CREATE INDEX idx_scores_session_log ON scores (session_log_id);

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

-- Competition WODs: an athlete chooses to submit a score for verification; staff only verify
-- submitted scores. Scores already on competition WODs were all meant for judging.
ALTER TABLE scores ADD COLUMN verification_requested BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE scores SET verification_requested = TRUE
WHERE wod_id IN (SELECT id FROM wods WHERE competition = TRUE);
