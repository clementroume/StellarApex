-- Daily sessions (lot B): a session is an ordered container of blocks proposed on a day (WOD session)
-- or a week (specialty session, date = Monday). A scored block points at a WOD, either created for
-- the session (standalone = false, hidden from WOD lists) or an existing standalone WOD (borrowed).
-- Warm-up / cool-down blocks are FR/EN text only.

ALTER TABLE wods ADD COLUMN standalone BOOLEAN NOT NULL DEFAULT TRUE;
CREATE INDEX idx_wods_standalone ON wods (standalone);

-- Prescribed number of sets of a movement (e.g. 5 with reps_scheme "5-5-3-3-1")
ALTER TABLE wod_movements ADD COLUMN sets INTEGER CHECK (sets >= 1);

CREATE TABLE sessions
(
    id                   BIGSERIAL PRIMARY KEY,
    version              BIGINT       NOT NULL DEFAULT 0,
    session_type         VARCHAR(20)  NOT NULL CHECK (session_type IN ('WOD', 'SPECIALTY')),
    discipline           VARCHAR(20)  NOT NULL,
    -- Day of a WOD session, Monday of the week of a specialty session
    date                 DATE         NOT NULL,
    -- Last day the session is proposed on (= date for a WOD session, date + 6 for a specialty)
    end_date             DATE         NOT NULL,
    title                VARCHAR(100),
    description_en       TEXT,
    description_fr       TEXT,
    gym_id               BIGINT,
    author_id            BIGINT       NOT NULL,
    -- Read rule of the content for the box's athletes (null for a personal session)
    visibility_policy    VARCHAR(30),
    -- Instant the content opens to the whole box (null: participants only, or personal session)
    content_visible_from TIMESTAMPTZ,
    created_at           TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_sessions_dates CHECK (end_date >= date)
);

CREATE INDEX idx_sessions_gym_date ON sessions (gym_id, date);
CREATE INDEX idx_sessions_author ON sessions (author_id) WHERE gym_id IS NULL;

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

CREATE INDEX idx_session_blocks_wod ON session_blocks (wod_id);

-- Session settings of a box: default visibility policy and the time zone used to compute
-- "the day of the session at 18:00" or "the next day".
CREATE TABLE gym_session_settings
(
    gym_id             BIGINT PRIMARY KEY,
    default_visibility VARCHAR(30) NOT NULL,
    time_zone          VARCHAR(50) NOT NULL DEFAULT 'Europe/Paris'
);
