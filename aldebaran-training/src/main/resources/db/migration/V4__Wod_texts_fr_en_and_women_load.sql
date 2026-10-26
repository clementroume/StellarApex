-- WOD texts become bilingual (existing texts were written in English) and a WOD movement gets
-- the women's load next to the men's one (same unit), shown as "@43/30 kg".
ALTER TABLE wods RENAME COLUMN description TO description_en;
ALTER TABLE wods RENAME COLUMN notes TO notes_en;
ALTER TABLE wods
    ADD COLUMN description_fr TEXT,
    ADD COLUMN notes_fr       TEXT;

ALTER TABLE wod_movements RENAME COLUMN notes TO notes_en;
ALTER TABLE wod_movements RENAME COLUMN scaling_options TO scaling_options_en;
ALTER TABLE wod_movements
    ADD COLUMN notes_fr           TEXT,
    ADD COLUMN scaling_options_fr TEXT,
    ADD COLUMN weight_female      DOUBLE PRECISION
        CONSTRAINT check_weight_female_positive CHECK (weight_female >= 0);
