-- Athlete profile (lot H, all optional): competition division (load shown, separate leaderboards),
-- birth year (age divisions), height. Height and body weights are private: only their owner reads them.
ALTER TABLE users
    ADD COLUMN division VARCHAR(10),
    ADD COLUMN birth_year INTEGER,
    ADD COLUMN height_cm INTEGER;
ALTER TABLE users
    ADD CONSTRAINT chk_users_division CHECK (division IN ('MEN', 'WOMEN')),
    ADD CONSTRAINT chk_users_birth_year CHECK (birth_year BETWEEN 1900 AND 2100),
    ADD CONSTRAINT chk_users_height CHECK (height_cm BETWEEN 100 AND 250);

-- Body weight history: one measure per user and day, in kilograms
CREATE TABLE body_weights
(
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    measured_on DATE          NOT NULL,
    weight_kg   NUMERIC(5, 2) NOT NULL CHECK (weight_kg BETWEEN 20 AND 400),
    created_at  TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_body_weights_user_day UNIQUE (user_id, measured_on)
);
