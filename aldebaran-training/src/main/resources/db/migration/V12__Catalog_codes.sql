-- Stable identity of the catalog (lot E, D191). The catalog migrations (db/catalog, R__ files) upsert
-- by these codes, so they never change once given: derived once from the name by the rule of
-- Muscle.codeOf (lowercase, every run of other characters becomes one dash), numbered when two names
-- give the same code. Names and titles stay editable.

-- Every movement is a catalog movement
ALTER TABLE movements ADD COLUMN code VARCHAR(100);

WITH derived AS (
    SELECT id,
           COALESCE(NULLIF(TRIM(BOTH '-' FROM REGEXP_REPLACE(LOWER(name), '[^a-z0-9]+', '-', 'g')), ''),
                    'movement') AS base
    FROM movements
),
numbered AS (
    SELECT id, base, ROW_NUMBER() OVER (PARTITION BY base ORDER BY id) AS rank
    FROM derived
)
UPDATE movements m
SET code = CASE WHEN n.rank = 1 THEN n.base ELSE n.base || '-' || n.rank END
FROM numbered n
WHERE n.id = m.id;

ALTER TABLE movements ALTER COLUMN code SET NOT NULL;
ALTER TABLE movements ADD CONSTRAINT uk_movements_code UNIQUE (code);

-- Only catalog WODs carry a code: public, without gym, published by the platform (gym, personal and
-- session WODs stay without one)
ALTER TABLE wods ADD COLUMN code VARCHAR(150);

WITH derived AS (
    SELECT id,
           COALESCE(NULLIF(TRIM(BOTH '-' FROM REGEXP_REPLACE(LOWER(title), '[^a-z0-9]+', '-', 'g')), ''),
                    'wod') AS base
    FROM wods
    WHERE is_public AND gym_id IS NULL
),
numbered AS (
    SELECT id, base, ROW_NUMBER() OVER (PARTITION BY base ORDER BY id) AS rank
    FROM derived
)
UPDATE wods w
SET code = CASE WHEN n.rank = 1 THEN n.base ELSE n.base || '-' || n.rank END
FROM numbered n
WHERE n.id = w.id;

ALTER TABLE wods ADD CONSTRAINT uk_wods_code UNIQUE (code);
