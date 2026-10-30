-- WOD formats v3: six categories (wod_type) and a sub-category (structure) guiding the entry.
-- Fran is a FOR_TIME / SCHEME; LADDER is kept for rungs going up or down (and pyramids).
ALTER TABLE wods ADD COLUMN structure VARCHAR(20);
-- Lines of a weightlifting complex done as one set (1 snatch pull + 1 snatch + 1 overhead squat)
ALTER TABLE wod_movements ADD COLUMN complex_group INTEGER CHECK (complex_group >= 1);

-- Reps of a dash scheme ("21-15-9") go only one way (descending or ascending)
CREATE FUNCTION pg_temp.monotone_scheme(scheme TEXT) RETURNS BOOLEAN LANGUAGE sql IMMUTABLE AS $$
  SELECT a = ARRAY(SELECT x FROM unnest(a) x ORDER BY x DESC)
      OR a = ARRAY(SELECT x FROM unnest(a) x ORDER BY x)
  FROM (SELECT string_to_array(regexp_replace(scheme, '\s', '', 'g'), '-')::INTEGER[] AS a) s
$$;

UPDATE wods SET structure = CASE
    WHEN rounds IS NOT NULL AND rounds > 1 THEN 'ROUNDS'
    WHEN rep_scheme ~ '^\s*\d+(\s*-\s*\d+)+\s*$'
      THEN CASE WHEN pg_temp.monotone_scheme(rep_scheme) THEN 'SCHEME' ELSE 'LADDER' END
    ELSE 'SINGLE' END
  WHERE wod_type = 'FOR_TIME';

UPDATE wods SET wod_type = 'FOR_TIME', structure = 'CHIPPER' WHERE wod_type = 'CHIPPER';

UPDATE wods SET structure = CASE
    WHEN ladder_start IS NOT NULL THEN 'LADDER'
    WHEN rep_scheme ~ '^\s*\d+(\s*-\s*\d+)+\s*$' AND pg_temp.monotone_scheme(rep_scheme) THEN 'SCHEME'
    ELSE 'LADDER' END,
  wod_type = CASE WHEN ladder_start IS NOT NULL AND score_type = 'REPS' THEN 'AMRAP' ELSE 'FOR_TIME' END
  WHERE wod_type = 'LADDER';

UPDATE wods SET structure = CASE WHEN ladder_start IS NOT NULL THEN 'LADDER' ELSE 'ROUNDS' END
  WHERE wod_type = 'AMRAP' AND structure IS NULL;

UPDATE wods SET structure = CASE WHEN emom_interval IS NULL OR emom_interval = 60 THEN 'EVERY_MINUTE'
    ELSE 'EVERY_X' END
  WHERE wod_type = 'EMOM';
UPDATE wods SET wod_type = 'EMOM', structure = 'INTERVALS' WHERE wod_type = 'INTERVALS';
UPDATE wods SET wod_type = 'EMOM', structure = 'DEATH_BY' WHERE wod_type = 'DEATH_BY';

UPDATE wods SET structure = CASE WHEN (work_seconds IS NULL OR work_seconds = 20)
    AND (rest_seconds IS NULL OR rest_seconds = 10) THEN 'CLASSIC' ELSE 'CUSTOM' END
  WHERE wod_type = 'TABATA';

UPDATE wods SET wod_type = 'FOR_LOAD', structure = CASE
    WHEN rep_scheme ~* '^\s*\d+\s*[x×]\s*\d+\s*$' THEN 'SETS'
    WHEN rep_scheme ~ '^\s*\d+(\s*-\s*\d+)+\s*$' THEN 'SCHEME'
    ELSE 'MAX' END
  WHERE wod_type = 'STRENGTH';

UPDATE wods SET wod_type = 'FOR_QUALITY', structure = 'PRACTICE' WHERE wod_type IN ('SKILL', 'ACCESSORY');

-- Rounds for max reps without a clock (Lynne)
UPDATE wods SET wod_type = 'AMRAP', structure = 'MAX_REPS' WHERE wod_type = 'CUSTOM';
