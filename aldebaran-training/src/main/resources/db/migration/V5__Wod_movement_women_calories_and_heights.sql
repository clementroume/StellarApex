-- Men/women values for calories and heights (box, wall-ball target), as written on the whiteboard
-- ("14/12 cal", "60/50 cm"). Unit names up to CENTIMETERS need wider unit columns.
ALTER TABLE wod_movements ALTER COLUMN distance_unit TYPE VARCHAR(20);
ALTER TABLE wod_movements
    ADD COLUMN calories_female INTEGER
        CONSTRAINT check_calories_female_positive CHECK (calories_female >= 0),
    ADD COLUMN height          DOUBLE PRECISION
        CONSTRAINT check_height_positive CHECK (height >= 0),
    ADD COLUMN height_female   DOUBLE PRECISION
        CONSTRAINT check_height_female_positive CHECK (height_female >= 0),
    ADD COLUMN height_unit     VARCHAR(20);

ALTER TABLE scores ALTER COLUMN distance_display_unit TYPE VARCHAR(20);
