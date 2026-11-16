-- Shuttle runs are written in repetitions (Open 23.2A: 1 rep = 25 ft out and 25 ft back), so the
-- usual name needs the value of one set in reps (D126): 30 shuttles, about 4 minutes of turns and
-- restarts, the time of one monostructural set (seconds_per_set 240). Without it these lines
-- counted 0 in the weekly balance (admin control, D121 rule 5). Distances keep the run's 800 m.
UPDATE movement_usual_names u
SET reps_per_set = 30
FROM movements m
WHERE u.movement_id = m.id
  AND m.name = 'Running'
  AND u.name_en = 'Shuttle Run';
