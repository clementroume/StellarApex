-- Division of the athlete (MEN / WOMEN) when the score was logged, copied from the X-Auth-User-Division
-- header (or from Antares for scores logged by staff), so leaderboards filter by division without
-- calling Antares. Scores logged before the athlete chose a division stay NULL until their next score
-- (ScoreService.logScore backfills them); NULL scores only appear on the "all divisions" leaderboard.
ALTER TABLE scores ADD COLUMN division VARCHAR(10);
ALTER TABLE scores ADD CONSTRAINT chk_scores_division CHECK (division IN ('MEN', 'WOMEN'));
CREATE INDEX idx_scores_wod_division ON scores (wod_id, division);
