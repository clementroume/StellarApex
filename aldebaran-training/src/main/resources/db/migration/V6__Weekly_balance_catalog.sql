-- Weekly balance (lot D): the catalog says what one set of a movement is worth (D126), a usual name
-- may carry its own modality (D127), and impact_factor goes away (D130).

-- 1. Value of one set, per unit of a line: reps, metres, seconds, calories (D126).
--    Read on the line's most precise usual name, else on the movement; nothing = the line counts 0.
ALTER TABLE movements
    ADD COLUMN reps_per_set     INTEGER CHECK (reps_per_set IS NULL OR reps_per_set > 0),
    ADD COLUMN meters_per_set   INTEGER CHECK (meters_per_set IS NULL OR meters_per_set > 0),
    ADD COLUMN seconds_per_set  INTEGER CHECK (seconds_per_set IS NULL OR seconds_per_set > 0),
    ADD COLUMN calories_per_set INTEGER CHECK (calories_per_set IS NULL OR calories_per_set > 0);

ALTER TABLE movement_usual_names
    ADD COLUMN reps_per_set     INTEGER CHECK (reps_per_set IS NULL OR reps_per_set > 0),
    ADD COLUMN meters_per_set   INTEGER CHECK (meters_per_set IS NULL OR meters_per_set > 0),
    ADD COLUMN seconds_per_set  INTEGER CHECK (seconds_per_set IS NULL OR seconds_per_set > 0),
    ADD COLUMN calories_per_set INTEGER CHECK (calories_per_set IS NULL OR calories_per_set > 0),
    ADD COLUMN modality         VARCHAR(30);

-- 2. Values of the existing catalog (D81 defaults, once; afterwards edited in the catalog forms).
UPDATE movements SET seconds_per_set = 60;
UPDATE movements SET reps_per_set = 10
WHERE category IN ('DEADLIFT', 'SQUAT', 'PRESS_AND_JERK', 'CLEAN', 'SNATCH', 'COMPLEXES', 'STRONGMAN_LIFTS',
                   'PULLING', 'PUSHING', 'INVERTED', 'LUNGES', 'LOCOMOTION_AND_BODY_CONTROL', 'BURPEES');
UPDATE movements SET reps_per_set = 15
WHERE category IN ('SWING', 'OTHER_LIFTS', 'THROWS_AND_SLAMS', 'CORE', 'JUMPING');
UPDATE movements SET meters_per_set = 50 WHERE category IN ('CARRY', 'SLED');
UPDATE movements SET meters_per_set = 800, seconds_per_set = 240 WHERE name = 'Running';
UPDATE movements SET meters_per_set = 400, seconds_per_set = 240 WHERE name = 'Swimming';
UPDATE movements SET meters_per_set = 1000, calories_per_set = 40, seconds_per_set = 240
WHERE name IN ('Rowing', 'Ski Erg');
UPDATE movements SET meters_per_set = 2000, calories_per_set = 50, seconds_per_set = 240 WHERE name = 'Biking';
UPDATE movements SET reps_per_set = 30 WHERE name IN ('Jumping Jack', 'Mountain Climber');
UPDATE movements SET reps_per_set = 15 WHERE name = 'Push-up';
UPDATE movements SET reps_per_set = 50 WHERE name = 'Jump Rope';
UPDATE movements SET reps_per_set = 5 WHERE name IN ('Bear Complex', 'Man Maker');
UPDATE movements SET reps_per_set = 8 WHERE name = 'Devil''s Press';
UPDATE movements SET meters_per_set = 10 WHERE name = 'Lunge';
UPDATE movements SET meters_per_set = 30 WHERE name = 'Broad Jump';
UPDATE movements SET meters_per_set = 25 WHERE name = 'Bear Crawl';

UPDATE movement_usual_names SET meters_per_set = 15 WHERE name_en = 'Handstand Walk';
UPDATE movement_usual_names SET seconds_per_set = 60 WHERE name_en = 'Handstand Hold';
UPDATE movement_usual_names SET reps_per_set = 100 WHERE name_en = 'Single-under';
UPDATE movement_usual_names SET reps_per_set = 50 WHERE name_en = 'Double-under';
UPDATE movement_usual_names SET reps_per_set = 25 WHERE name_en = 'Triple-under';
UPDATE movement_usual_names SET reps_per_set = 20 WHERE name_en = 'Air Squat';
UPDATE movement_usual_names SET reps_per_set = 20, meters_per_set = 20 WHERE name_en IN ('Lunge', 'Walking Lunge');

-- 3. Modality (D127): Jump Rope is cardio, not a jump; bodyweight usual names of loaded families.
UPDATE movements SET category = 'CARDIO' WHERE name = 'Jump Rope';
UPDATE movement_usual_names SET modality = 'GYMNASTICS'
WHERE name_en IN ('Air Squat', 'Split Squat', 'Bulgarian Split Squat', 'Glute Bridge');

-- Strongman is a specialty, no longer a modality: those WODs are weightlifting
DELETE FROM wod_modalities s
WHERE s.modality = 'STRONGMAN'
  AND EXISTS (SELECT 1 FROM wod_modalities w WHERE w.wod_id = s.wod_id AND w.modality = 'WEIGHTLIFTING');
UPDATE wod_modalities SET modality = 'WEIGHTLIFTING' WHERE modality = 'STRONGMAN';

-- 4. A loaded lunge names its load position, like a squat (D126): never read as the bodyweight name
INSERT INTO movement_required_rules (movement_id, rule)
SELECT id, 'LOAD_POSITION_IF_EQUIPMENT' FROM movements WHERE name = 'Lunge'
ON CONFLICT DO NOTHING;

INSERT INTO wod_movement_techniques (wod_movement_id, technique)
SELECT wm.id, 'FARMERS_CARRY'
FROM wod_movements wm
         JOIN wods w ON w.id = wm.wod_id
         JOIN movements m ON m.id = wm.movement_id
WHERE m.name = 'Lunge'
  AND w.title IN ('Strange', 'DG', 'Josh-O', 'Swift 1-1')
  AND w.is_public
  AND w.gym_id IS NULL
  AND EXISTS (SELECT 1 FROM wod_movement_equipment e WHERE e.wod_movement_id = wm.id)
  AND NOT EXISTS (SELECT 1
                  FROM wod_movement_techniques t
                  WHERE t.wod_movement_id = wm.id
                    AND t.technique IN ('FRONT_RACK', 'OVERHEAD', 'BEHIND_THE_NECK', 'BACK_RACK', 'HIGH_BAR',
                                        'LOW_BAR', 'BEAR_HUG', 'ZERCHER', 'ON_SHOULDER', 'OVER_SHOULDER',
                                        'GOBLET', 'FARMERS_CARRY'));

-- 5. impact_factor doubled the role weight (D46, D130): the role alone weighs a muscle
ALTER TABLE movement_muscles DROP CONSTRAINT check_impact_factor_range;
ALTER TABLE movement_muscles DROP COLUMN impact_factor;
