-- Gym where a score was performed: the WOD's gym for a gym WOD, or one of the athlete's gyms
-- (chosen when logging) for a public or personal WOD. Drives gym leaderboards and staff moderation.
ALTER TABLE scores ADD COLUMN gym_id BIGINT;

UPDATE scores s
SET gym_id = w.gym_id
FROM wods w
WHERE s.wod_id = w.id
  AND w.gym_id IS NOT NULL;

CREATE INDEX idx_scores_wod_gym ON scores (wod_id, gym_id);
