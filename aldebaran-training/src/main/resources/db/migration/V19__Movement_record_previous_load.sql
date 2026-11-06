-- The load a record beat (best load before the record's day), for "+5 kg" on the records board;
-- filled by the next rebuild (startup backfill reruns when the table is emptied)
ALTER TABLE movement_records ADD COLUMN previous_weight_kg DOUBLE PRECISION CHECK (previous_weight_kg >= 0);

-- Rebuild every record with its previous load at the next start (MovementRecordBackfill)
DELETE FROM movement_records;
