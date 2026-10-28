-- UI colour palette, combined with the light/dark theme: ocean (default), chalk, volt or rig.
ALTER TABLE users
    ADD COLUMN palette VARCHAR(20) NOT NULL DEFAULT 'ocean',
    ADD CONSTRAINT chk_palette CHECK (palette IN ('ocean', 'chalk', 'volt', 'rig'));
