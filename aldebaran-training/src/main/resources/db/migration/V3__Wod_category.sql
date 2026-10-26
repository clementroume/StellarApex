-- A WOD now has a format (wod_type: FOR_TIME, AMRAP, EMOM...) and an optional category
-- (GIRLS, HERO, OPEN...): Fran is a For Time workout of the Girls category.
ALTER TABLE wods
    ADD COLUMN category VARCHAR(50);

UPDATE wods
SET category = wod_type
WHERE wod_type IN ('GIRLS', 'HERO', 'BENCHMARK', 'CUSTOM_BENCHMARK', 'OPEN', 'QUARTERFINALS',
                   'SEMIFINALS', 'GAMES');

-- Categorized WODs get the format their score implies
UPDATE wods
SET wod_type = CASE
                   WHEN emom_interval IS NOT NULL AND emom_interval > 0 THEN 'EMOM'
                   WHEN score_type = 'TIME' THEN 'FOR_TIME'
                   WHEN score_type IN ('LOAD', 'WEIGHT') THEN 'STRENGTH'
                   ELSE 'AMRAP'
    END
WHERE category IS NOT NULL;

-- A chipper is a For Time workout
UPDATE wods
SET wod_type = 'FOR_TIME'
WHERE wod_type = 'CHIPPER';

CREATE INDEX idx_wods_category ON wods (category);
