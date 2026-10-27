-- Competition WODs: gym WODs on which staff holding SCORE_VERIFY log scores for athletes and
-- verify them (judging). A copied WOD keeps a link to the WOD it was copied from.
ALTER TABLE wods ADD COLUMN competition BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE wods ADD COLUMN source_wod_id BIGINT REFERENCES wods (id) ON DELETE SET NULL;

-- Who logged a score (the athlete, or the staff / admin logging it for them), and who verified it.
ALTER TABLE scores ADD COLUMN logged_by BIGINT;
UPDATE scores SET logged_by = user_id;
ALTER TABLE scores ALTER COLUMN logged_by SET NOT NULL;

ALTER TABLE scores ADD COLUMN verified_by BIGINT;
ALTER TABLE scores ADD COLUMN verified_at TIMESTAMP;
