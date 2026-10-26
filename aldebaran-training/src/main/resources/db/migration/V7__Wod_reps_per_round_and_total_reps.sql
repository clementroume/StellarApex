-- Reps of one round of an AMRAP with a fixed round (Cindy: 5 + 10 + 15 = 30); NULL otherwise
ALTER TABLE wods ADD COLUMN reps_per_round INTEGER;
ALTER TABLE wods ADD CONSTRAINT check_reps_per_round_positive CHECK (reps_per_round IS NULL OR reps_per_round > 0);

-- ROUNDS_REPS WODs: derive the reps per round when every movement has a whole number of reps or
-- calories (a calorie counts as a rep, as in the Open); distances or ladders leave it NULL
UPDATE wods w
SET reps_per_round = r.total
FROM (SELECT wm.wod_id,
             SUM(CASE
                     WHEN wm.reps_scheme ~ '^\s*\d+\s*$' THEN TRIM(wm.reps_scheme)::INTEGER
                     WHEN (wm.reps_scheme IS NULL OR TRIM(wm.reps_scheme) = '') AND wm.distance IS NULL
                         THEN wm.calories
                     END)                     AS total,
             -- COALESCE: a NULL (no reps) must count as "not fixed", BOOL_AND would skip it
             BOOL_AND(COALESCE(wm.reps_scheme ~ '^\s*\d+\s*$', FALSE)
                 OR ((wm.reps_scheme IS NULL OR TRIM(wm.reps_scheme) = '')
                     AND wm.distance IS NULL AND wm.calories IS NOT NULL)) AS fixed
      FROM wod_movements wm
      GROUP BY wm.wod_id) r
WHERE w.id = r.wod_id
  AND w.score_type = 'ROUNDS_REPS'
  AND r.fixed
  AND r.total > 0;

-- Their scores become total reps (rounds * reps per round + extra reps). Scores of a WOD whose
-- round could not be derived keep their columns untouched, so nothing is lost
UPDATE scores s
SET reps   = COALESCE(s.rounds, 0) * w.reps_per_round + COALESCE(s.reps, 0),
    rounds = NULL
FROM wods w
WHERE s.wod_id = w.id
  AND w.score_type = 'ROUNDS_REPS'
  AND w.reps_per_round IS NOT NULL;

-- AMRAPs are now always scored in total reps
UPDATE wods SET score_type = 'REPS' WHERE score_type = 'ROUNDS_REPS';
