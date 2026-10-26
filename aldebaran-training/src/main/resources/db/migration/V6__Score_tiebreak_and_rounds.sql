-- Official tiebreak of a WOD (how the time is taken, EN / FR); NULL when the WOD has none
ALTER TABLE wods ADD COLUMN tiebreak_en TEXT;
ALTER TABLE wods ADD COLUMN tiebreak_fr TEXT;

-- Optional tiebreak time of a score (elapsed seconds), only meaningful for WODs with a tiebreak
ALTER TABLE scores ADD COLUMN tiebreak_seconds INTEGER;
ALTER TABLE scores ADD CONSTRAINT check_tiebreak_positive CHECK (tiebreak_seconds IS NULL OR tiebreak_seconds > 0);
