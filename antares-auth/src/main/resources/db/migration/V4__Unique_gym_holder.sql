-- A gym has exactly one holder: OWNER (physical gym) or PROGRAMMER (virtual gym).
-- Refuse to migrate if existing data breaks the rule, so it can be fixed by hand first.
DO
$$
DECLARE
    duplicate RECORD;
BEGIN
    SELECT gym_id, count(*) AS holders
    INTO duplicate
    FROM memberships
    WHERE gym_role IN ('OWNER', 'PROGRAMMER')
    GROUP BY gym_id
    HAVING count(*) > 1
    ORDER BY gym_id
    LIMIT 1;

    IF FOUND THEN
        RAISE EXCEPTION 'Gym % has % OWNER/PROGRAMMER memberships: keep exactly one holder per gym (demote the others) before applying V4.',
            duplicate.gym_id, duplicate.holders;
    END IF;
END
$$;

CREATE UNIQUE INDEX uq_memberships_gym_holder ON memberships (gym_id)
    WHERE gym_role IN ('OWNER', 'PROGRAMMER');
