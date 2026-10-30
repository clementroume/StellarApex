-- Team WODs: team size and how the work is shared; each WOD movement is done in total, by each
-- member, or together. A team score is one performance shared by its members: each member gets the
-- same score row (records, journal, history) linked to the team; leaderboards show one row per team.
ALTER TABLE wods ADD COLUMN team_size INTEGER CHECK (team_size BETWEEN 2 AND 6);
ALTER TABLE wods ADD COLUMN team_mode VARCHAR(20)
    CHECK (team_mode IN ('YOU_GO_I_GO', 'SPLIT', 'SYNCHRO'));
ALTER TABLE wod_movements ADD COLUMN team_split VARCHAR(10) NOT NULL DEFAULT 'TOTAL'
    CHECK (team_split IN ('TOTAL', 'EACH', 'TOGETHER'));

CREATE TABLE score_teams (
    id         BIGSERIAL PRIMARY KEY,
    wod_id     BIGINT    NOT NULL REFERENCES wods (id) ON DELETE CASCADE,
    gym_id     BIGINT    NOT NULL,
    created_by BIGINT    NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE score_team_members (
    team_id BIGINT NOT NULL REFERENCES score_teams (id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL,
    PRIMARY KEY (team_id, user_id)
);

ALTER TABLE scores ADD COLUMN team_id BIGINT REFERENCES score_teams (id) ON DELETE SET NULL;
CREATE INDEX idx_scores_team ON scores (team_id);

-- Team division: men, women, or mixed (to refine one day: HHF, FFM...)
ALTER TABLE scores DROP CONSTRAINT chk_scores_division;
ALTER TABLE scores ADD CONSTRAINT chk_scores_division CHECK (division IN ('MEN', 'WOMEN', 'MIXED'));
