-- Lot C: per-movement records (D47-D62).

-- N of an "N RM" test (For Load WOD with one movement): its score counts as an N-rep record
ALTER TABLE wods ADD COLUMN rep_max INTEGER CHECK (rep_max BETWEEN 1 AND 50);

-- Existing "1RM", "3 RM"... For Load WODs
UPDATE wods
SET rep_max = CAST(substring(rep_scheme FROM '^\s*(\d+)\s*[Rr][Mm]\s*$') AS INTEGER)
WHERE wod_type = 'FOR_LOAD'
  AND rep_scheme ~ '^\s*\d+\s*[Rr][Mm]\s*$'
  AND CAST(substring(rep_scheme FROM '^\s*(\d+)\s*[Rr][Mm]\s*$') AS INTEGER) BETWEEN 1 AND 50;

-- Load prescribed as a percentage of the athlete's N RM of the movement (base version, D58-D59)
ALTER TABLE wod_movements ADD COLUMN percent_of_rm DOUBLE PRECISION CHECK (percent_of_rm > 0 AND percent_of_rm <= 150);
ALTER TABLE wod_movements ADD COLUMN rm_reps INTEGER CHECK (rm_reps BETWEEN 1 AND 50);

-- Age group of the athlete in the year of the score (CrossFit Open brackets), copied like the
-- division; the birth year itself never leaves Antares' storage
ALTER TABLE scores ADD COLUMN age_group VARCHAR(20);

-- Best load per athlete, movement, variant set and number of reps, rebuilt from the scores
CREATE TABLE movement_records
(
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT           NOT NULL,
    movement_id BIGINT           NOT NULL REFERENCES movements (id) ON DELETE CASCADE,
    -- Sorted equipment and techniques of the WOD line, implied equipment removed; '' = base version
    variant_key VARCHAR(500)     NOT NULL DEFAULT '',
    reps        INTEGER          NOT NULL CHECK (reps >= 1),
    weight_kg   DOUBLE PRECISION NOT NULL CHECK (weight_kg >= 0),
    date        DATE             NOT NULL,
    score_id    BIGINT           NOT NULL REFERENCES scores (id) ON DELETE CASCADE,
    division    VARCHAR(10),
    age_group   VARCHAR(20),
    CONSTRAINT uk_movement_record UNIQUE (user_id, movement_id, variant_key, reps)
);

CREATE INDEX idx_movement_records_board ON movement_records (movement_id, variant_key, reps);
CREATE INDEX idx_movement_records_score ON movement_records (score_id);
