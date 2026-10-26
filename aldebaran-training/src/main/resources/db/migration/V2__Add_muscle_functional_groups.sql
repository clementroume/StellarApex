-- Functional groups: a muscle may belong to one functional group (e.g. Rectus Femoris -> Quadriceps
-- Femoris). One level only, enforced by MuscleService. Deleting a group keeps its muscles.
ALTER TABLE muscles
    ADD COLUMN parent_id BIGINT REFERENCES muscles (id) ON DELETE SET NULL;

CREATE INDEX idx_muscles_parent_id ON muscles (parent_id);
