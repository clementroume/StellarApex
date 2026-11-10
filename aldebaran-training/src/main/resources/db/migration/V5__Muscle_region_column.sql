-- "Group" now only means a functional group (parent / members): the region gets its own name.
ALTER TABLE muscles RENAME COLUMN muscle_group TO region;
