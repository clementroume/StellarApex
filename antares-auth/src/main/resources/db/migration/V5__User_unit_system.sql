-- Display unit for loads: METRIC (kg) or IMPERIAL (lb). NULL = follow the language (fr: kg).
ALTER TABLE users
    ADD COLUMN unit_system VARCHAR(10);
