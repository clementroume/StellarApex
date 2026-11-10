-- Stable key linking a muscle to its 3D model and images: derived once from the medical name, never
-- changed afterwards (medical_name stays editable). Same rule as Muscle.codeOf; names that would give
-- the same code are numbered.
ALTER TABLE muscles ADD COLUMN code VARCHAR(100);

WITH derived AS (
    SELECT id,
           COALESCE(NULLIF(TRIM(BOTH '-' FROM REGEXP_REPLACE(LOWER(medical_name), '[^a-z0-9]+', '-', 'g')), ''),
                    'muscle') AS base
    FROM muscles
),
numbered AS (
    SELECT id, base, ROW_NUMBER() OVER (PARTITION BY base ORDER BY id) AS rank
    FROM derived
)
UPDATE muscles m
SET code = CASE WHEN n.rank = 1 THEN n.base ELSE n.base || '-' || n.rank END
FROM numbered n
WHERE n.id = m.id;

ALTER TABLE muscles ALTER COLUMN code SET NOT NULL;
ALTER TABLE muscles ADD CONSTRAINT uk_muscles_code UNIQUE (code);
