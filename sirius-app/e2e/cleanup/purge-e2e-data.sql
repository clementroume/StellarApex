-- Removes the data left by the Playwright journeys, on the LOCAL dev database only.
-- Psql variables: :'accounts' = comma-separated e-mails of the e2e test accounts, :commit = 1 to commit
-- (anything else rolls back: dry run that only prints the counts).
-- Double key: WOD/session title starts with "E2E " AND its author is one of the test accounts.
-- Scored WODs and sessions with a journal are refused by the app (409: ON DELETE RESTRICT), hence this script.
BEGIN;

CREATE TEMP TABLE e2e_authors ON COMMIT DROP AS
  SELECT id FROM antares.users WHERE email = ANY (string_to_array(:'accounts', ','));
CREATE TEMP TABLE e2e_sessions ON COMMIT DROP AS
  SELECT id FROM aldebaran.sessions
   WHERE title LIKE 'E2E %' AND author_id IN (SELECT id FROM e2e_authors);
-- Standalone WODs of the journeys, plus the WODs written inside the blocks of an E2E session (owned by their block)
CREATE TEMP TABLE e2e_wods ON COMMIT DROP AS
  SELECT id FROM aldebaran.wods
   WHERE title LIKE 'E2E %' AND author_id IN (SELECT id FROM e2e_authors)
  UNION
  SELECT w.id FROM aldebaran.wods w
    JOIN aldebaran.session_blocks b ON b.wod_id = w.id
   WHERE b.session_id IN (SELECT id FROM e2e_sessions) AND NOT w.standalone;
-- Accounts created by the registration journey (e2e+<timestamp>@test.local; e2e.<timestamp> = former pattern)
CREATE TEMP TABLE e2e_users ON COMMIT DROP AS
  SELECT id FROM antares.users WHERE email ~ '^e2e[.+][0-9]+@test\.local$';

-- Aldebaran, children first
CREATE TEMP TABLE e2e_scores ON COMMIT DROP AS
  SELECT id FROM aldebaran.scores
   WHERE wod_id IN (SELECT id FROM e2e_wods)
      OR session_block_id IN (SELECT id FROM aldebaran.session_blocks WHERE session_id IN (SELECT id FROM e2e_sessions))
      OR session_log_id IN (SELECT id FROM aldebaran.session_logs WHERE session_id IN (SELECT id FROM e2e_sessions));
CREATE TEMP TABLE e2e_counts (step text, n bigint) ON COMMIT DROP;

WITH d AS (DELETE FROM aldebaran.score_sets WHERE score_id IN (SELECT id FROM e2e_scores) RETURNING 1)
  INSERT INTO e2e_counts SELECT 'score_sets', count(*) FROM d;
WITH d AS (DELETE FROM aldebaran.scores WHERE id IN (SELECT id FROM e2e_scores) RETURNING 1)
  INSERT INTO e2e_counts SELECT 'scores', count(*) FROM d;
WITH d AS (DELETE FROM aldebaran.score_teams WHERE wod_id IN (SELECT id FROM e2e_wods) RETURNING 1)
  INSERT INTO e2e_counts SELECT 'score_teams', count(*) FROM d;
WITH d AS (DELETE FROM aldebaran.session_log_blocks WHERE log_id IN (SELECT id FROM aldebaran.session_logs WHERE session_id IN (SELECT id FROM e2e_sessions)) RETURNING 1)
  INSERT INTO e2e_counts SELECT 'session_log_blocks', count(*) FROM d;
WITH d AS (DELETE FROM aldebaran.session_logs WHERE session_id IN (SELECT id FROM e2e_sessions) RETURNING 1)
  INSERT INTO e2e_counts SELECT 'session_logs', count(*) FROM d;
WITH d AS (DELETE FROM aldebaran.session_blocks WHERE session_id IN (SELECT id FROM e2e_sessions) RETURNING 1)
  INSERT INTO e2e_counts SELECT 'session_blocks', count(*) FROM d;
WITH d AS (DELETE FROM aldebaran.sessions WHERE id IN (SELECT id FROM e2e_sessions) RETURNING 1)
  INSERT INTO e2e_counts SELECT 'sessions', count(*) FROM d;
WITH d AS (DELETE FROM aldebaran.wod_movement_equipment WHERE wod_movement_id IN (SELECT id FROM aldebaran.wod_movements WHERE wod_id IN (SELECT id FROM e2e_wods)) RETURNING 1)
  INSERT INTO e2e_counts SELECT 'wod_movement_equipment', count(*) FROM d;
WITH d AS (DELETE FROM aldebaran.wod_movement_techniques WHERE wod_movement_id IN (SELECT id FROM aldebaran.wod_movements WHERE wod_id IN (SELECT id FROM e2e_wods)) RETURNING 1)
  INSERT INTO e2e_counts SELECT 'wod_movement_techniques', count(*) FROM d;
WITH d AS (DELETE FROM aldebaran.wod_movements WHERE wod_id IN (SELECT id FROM e2e_wods) RETURNING 1)
  INSERT INTO e2e_counts SELECT 'wod_movements', count(*) FROM d;
WITH d AS (DELETE FROM aldebaran.wod_modalities WHERE wod_id IN (SELECT id FROM e2e_wods) RETURNING 1)
  INSERT INTO e2e_counts SELECT 'wod_modalities', count(*) FROM d;
-- A WOD still borrowed by a session that is not an E2E one is kept (RESTRICT)
WITH d AS (DELETE FROM aldebaran.wods w WHERE w.id IN (SELECT id FROM e2e_wods)
             AND NOT EXISTS (SELECT 1 FROM aldebaran.session_blocks b WHERE b.wod_id = w.id) RETURNING 1)
  INSERT INTO e2e_counts SELECT 'wods', count(*) FROM d;

-- Antares: memberships first, then the accounts of the registration journey
WITH d AS (DELETE FROM antares.membership_permissions WHERE membership_id IN (SELECT id FROM antares.memberships WHERE user_id IN (SELECT id FROM e2e_users)) RETURNING 1)
  INSERT INTO e2e_counts SELECT 'membership_permissions', count(*) FROM d;
WITH d AS (DELETE FROM antares.memberships WHERE user_id IN (SELECT id FROM e2e_users) RETURNING 1)
  INSERT INTO e2e_counts SELECT 'memberships', count(*) FROM d;
WITH d AS (DELETE FROM antares.users WHERE id IN (SELECT id FROM e2e_users) RETURNING 1)
  INSERT INTO e2e_counts SELECT 'users', count(*) FROM d;

SELECT step, n FROM e2e_counts ORDER BY 1;
\if :{?commit}
  \echo COMMIT
  COMMIT;
\else
  \echo DRY RUN (rolled back): pass -v commit=1 to apply
  ROLLBACK;
\endif
