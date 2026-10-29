-- Structured WOD prescription (quick WOD editor v2): rounds of a "rounds for time", rest between
-- rounds and work time of intervals, ascending ladder (start, step), weight vest; a WOD movement
-- belongs to the buy-in, the rounds (MAIN) or the buy-out. New formats CHIPPER, LADDER, DEATH_BY
-- are plain wod_type values.
ALTER TABLE wods ADD COLUMN rounds INTEGER CHECK (rounds >= 1);
ALTER TABLE wods ADD COLUMN rest_seconds INTEGER CHECK (rest_seconds >= 0);
ALTER TABLE wods ADD COLUMN work_seconds INTEGER CHECK (work_seconds >= 1);
ALTER TABLE wods ADD COLUMN ladder_start INTEGER CHECK (ladder_start >= 1);
ALTER TABLE wods ADD COLUMN ladder_step INTEGER CHECK (ladder_step >= 1);
ALTER TABLE wods ADD COLUMN vest_weight DOUBLE PRECISION CHECK (vest_weight >= 0);
ALTER TABLE wods ADD COLUMN vest_weight_female DOUBLE PRECISION CHECK (vest_weight_female >= 0);
ALTER TABLE wods ADD COLUMN vest_weight_unit VARCHAR(10);

ALTER TABLE wod_movements ADD COLUMN segment VARCHAR(10) NOT NULL DEFAULT 'MAIN'
    CHECK (segment IN ('BUY_IN', 'MAIN', 'BUY_OUT'));
