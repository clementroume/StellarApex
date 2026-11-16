-- Lot S (score entry), decisions D140 to D154.

-- ----------------------------------------------------------------------------------
-- Scaling levels: RX and SCALED only (D140). ELITE and CUSTOM become SCALED; an Rx+ is a computed
-- badge, and a Scaled score that follows the WOD's official Scaled version is flagged (D141).
UPDATE scores SET scaling = 'SCALED' WHERE scaling NOT IN ('RX', 'SCALED');
ALTER TABLE scores
    ADD CONSTRAINT chk_scores_scaling CHECK (scaling IN ('RX', 'SCALED')),
    ADD COLUMN scaled_official BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN rx_plus         BOOLEAN NOT NULL DEFAULT FALSE;

-- The free scaling note leaves for the comment (D152): what it described is now per line
UPDATE scores
SET user_comment = CASE
                       WHEN user_comment IS NULL OR btrim(user_comment) = '' THEN scaling_notes
                       ELSE user_comment || E'\n\n' || scaling_notes END
WHERE scaling_notes IS NOT NULL
  AND btrim(scaling_notes) <> '';
ALTER TABLE scores DROP COLUMN scaling_notes;

-- ----------------------------------------------------------------------------------
-- Strength sets belong to a line of the WOD (D143); a set of a complex is one round, linked to
-- the complex's first line. Sets get their own id (an entity of their own).
ALTER TABLE score_sets
    DROP CONSTRAINT score_sets_pkey,
    ADD COLUMN id              BIGSERIAL PRIMARY KEY,
    ADD COLUMN wod_movement_id BIGINT REFERENCES wod_movements (id) ON DELETE CASCADE,
    ADD CONSTRAINT uk_score_set_index UNIQUE (score_id, set_index);

-- Former sets were not linked: the first main line of the WOD (single line or complex)
UPDATE score_sets ss
SET wod_movement_id = (SELECT wm.id
                       FROM scores s
                                JOIN wod_movements wm ON wm.wod_id = s.wod_id
                       WHERE s.id = ss.score_id
                       ORDER BY (wm.segment = 'MAIN') DESC, wm.order_index
                       LIMIT 1);
DELETE FROM score_sets WHERE wod_movement_id IS NULL;
ALTER TABLE score_sets ALTER COLUMN wod_movement_id SET NOT NULL;
CREATE INDEX idx_score_sets_line ON score_sets (wod_movement_id);

-- ----------------------------------------------------------------------------------
-- Lines done otherwise than prescribed (D142): one row per changed line only; the variant done
-- (movement, equipment, techniques) and the quantities the line prescribes, a load in kg and a
-- distance or height in meters (performances, base unit). On a "max" line of a WOD with several,
-- reps_scheme holds the reps counted (D147), not a change.
CREATE TABLE score_lines
(
    id               BIGSERIAL PRIMARY KEY,
    score_id         BIGINT NOT NULL REFERENCES scores (id) ON DELETE CASCADE,
    wod_movement_id  BIGINT NOT NULL REFERENCES wod_movements (id) ON DELETE CASCADE,
    movement_id      BIGINT NOT NULL REFERENCES movements (id),
    reps_scheme      VARCHAR(50),
    weight_kg        DOUBLE PRECISION CHECK (weight_kg IS NULL OR weight_kg >= 0),
    distance_meters  DOUBLE PRECISION CHECK (distance_meters IS NULL OR distance_meters >= 0),
    calories         INTEGER CHECK (calories IS NULL OR calories >= 0),
    height_meters    DOUBLE PRECISION CHECK (height_meters IS NULL OR height_meters >= 0),
    duration_seconds INTEGER CHECK (duration_seconds IS NULL OR duration_seconds >= 0),
    CONSTRAINT uk_score_line UNIQUE (score_id, wod_movement_id)
);
CREATE INDEX idx_score_lines_line ON score_lines (wod_movement_id);

CREATE TABLE score_line_equipment
(
    score_line_id BIGINT      NOT NULL REFERENCES score_lines (id) ON DELETE CASCADE,
    equipment     VARCHAR(50) NOT NULL,
    PRIMARY KEY (score_line_id, equipment)
);

CREATE TABLE score_line_techniques
(
    score_line_id BIGINT      NOT NULL REFERENCES score_lines (id) ON DELETE CASCADE,
    technique     VARCHAR(50) NOT NULL,
    PRIMARY KEY (score_line_id, technique)
);

-- ----------------------------------------------------------------------------------
-- Official Scaled version of a WOD (D141, D154): one row per line changed in that version, a
-- prescription kept in its unit of origin like the RX line (D149).
CREATE TABLE wod_scaled_lines
(
    id                    BIGSERIAL PRIMARY KEY,
    wod_movement_id       BIGINT NOT NULL UNIQUE REFERENCES wod_movements (id) ON DELETE CASCADE,
    movement_id           BIGINT NOT NULL REFERENCES movements (id),
    reps_scheme           VARCHAR(50),
    weight                DOUBLE PRECISION CHECK (weight IS NULL OR weight >= 0),
    weight_female         DOUBLE PRECISION CHECK (weight_female IS NULL OR weight_female >= 0),
    weight_unit           VARCHAR(10),
    distance              DOUBLE PRECISION CHECK (distance IS NULL OR distance >= 0),
    distance_unit         VARCHAR(20),
    calories              INTEGER CHECK (calories IS NULL OR calories >= 0),
    calories_female       INTEGER CHECK (calories_female IS NULL OR calories_female >= 0),
    height                DOUBLE PRECISION CHECK (height IS NULL OR height >= 0),
    height_female         DOUBLE PRECISION CHECK (height_female IS NULL OR height_female >= 0),
    height_unit           VARCHAR(20),
    duration_seconds      INTEGER CHECK (duration_seconds IS NULL OR duration_seconds >= 0),
    CONSTRAINT chk_scaled_weight_unit CHECK (weight_unit IS NULL OR weight_unit IN ('KG', 'LBS')),
    CONSTRAINT chk_scaled_distance_unit CHECK (distance_unit IS NULL OR distance_unit IN
        ('METERS', 'KILOMETERS', 'CENTIMETERS', 'INCHES', 'FEET', 'YARDS', 'MILES')),
    CONSTRAINT chk_scaled_height_unit CHECK (height_unit IS NULL OR height_unit IN
        ('METERS', 'CENTIMETERS', 'INCHES', 'FEET'))
);

CREATE TABLE wod_scaled_line_equipment
(
    wod_scaled_line_id BIGINT      NOT NULL REFERENCES wod_scaled_lines (id) ON DELETE CASCADE,
    equipment          VARCHAR(50) NOT NULL,
    PRIMARY KEY (wod_scaled_line_id, equipment)
);

CREATE TABLE wod_scaled_line_techniques
(
    wod_scaled_line_id BIGINT      NOT NULL REFERENCES wod_scaled_lines (id) ON DELETE CASCADE,
    technique          VARCHAR(50) NOT NULL,
    PRIMARY KEY (wod_scaled_line_id, technique)
);

-- ----------------------------------------------------------------------------------
-- The free "scaling options" text of a line leaves for its notes (D154): the official Scaled
-- version replaces it.
UPDATE wod_movements
SET notes_en = CASE
                   WHEN notes_en IS NULL OR btrim(notes_en) = '' THEN 'Scaling: ' || scaling_options_en
                   ELSE notes_en || E'\n\nScaling: ' || scaling_options_en END
WHERE scaling_options_en IS NOT NULL
  AND btrim(scaling_options_en) <> '';
UPDATE wod_movements
SET notes_fr = CASE
                   WHEN notes_fr IS NULL OR btrim(notes_fr) = '' THEN 'Scaling : ' || scaling_options_fr
                   ELSE notes_fr || E'\n\nScaling : ' || scaling_options_fr END
WHERE scaling_options_fr IS NOT NULL
  AND btrim(scaling_options_fr) <> '';
ALTER TABLE wod_movements
    DROP COLUMN scaling_options_en,
    DROP COLUMN scaling_options_fr;

-- ----------------------------------------------------------------------------------
-- Every unit column holds a unit of its kind (D150): a height in KG no longer passes.
ALTER TABLE wod_movements
    ADD CONSTRAINT chk_wm_weight_unit CHECK (weight_unit IS NULL OR weight_unit IN ('KG', 'LBS')),
    ADD CONSTRAINT chk_wm_duration_unit CHECK (duration_display_unit IS NULL
        OR duration_display_unit IN ('SECONDS', 'MINUTES')),
    ADD CONSTRAINT chk_wm_distance_unit CHECK (distance_unit IS NULL OR distance_unit IN
        ('METERS', 'KILOMETERS', 'CENTIMETERS', 'INCHES', 'FEET', 'YARDS', 'MILES')),
    ADD CONSTRAINT chk_wm_height_unit CHECK (height_unit IS NULL OR height_unit IN
        ('METERS', 'CENTIMETERS', 'INCHES', 'FEET')),
    ADD CONSTRAINT chk_wm_length_unit CHECK (length_unit IS NULL OR length_unit IN
        ('METERS', 'CENTIMETERS', 'INCHES', 'FEET', 'YARDS'));
ALTER TABLE wods
    ADD CONSTRAINT chk_wods_vest_unit CHECK (vest_weight_unit IS NULL
        OR vest_weight_unit IN ('KG', 'LBS'));
ALTER TABLE scores
    ADD CONSTRAINT chk_scores_time_unit CHECK (time_display_unit IS NULL
        OR time_display_unit IN ('SECONDS', 'MINUTES')),
    ADD CONSTRAINT chk_scores_weight_unit CHECK (weight_display_unit IS NULL
        OR weight_display_unit IN ('KG', 'LBS')),
    ADD CONSTRAINT chk_scores_distance_unit CHECK (distance_display_unit IS NULL
        OR distance_display_unit IN
           ('METERS', 'KILOMETERS', 'CENTIMETERS', 'INCHES', 'FEET', 'YARDS', 'MILES'));

-- ----------------------------------------------------------------------------------
-- Cardio values of one set (D151, corrects D126): one set = 800 m of running, so 1,000 m or
-- 80 cal of rowing or skiing, 2,000 m or 80 cal on a BikeErg, 60 cal on an air bike.
UPDATE movements SET calories_per_set = 80 WHERE name IN ('Rowing', 'Ski Erg', 'Biking');
UPDATE movement_usual_names u
SET calories_per_set = 60
FROM movements m
WHERE u.movement_id = m.id
  AND m.name = 'Biking'
  AND u.name_en IN ('Echo Bike', 'Assault Bike');

-- ----------------------------------------------------------------------------------
-- Records are fed by every strength line done (D144): rebuilt at startup when empty
-- (MovementRecordBackfill). The weekly balance reads the lines done (rules version 3), and every
-- stored week is rebuilt on its next display.
TRUNCATE movement_records;
UPDATE balance_weeks SET stale = TRUE;
