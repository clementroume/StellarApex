-- Consent to show the user's name on public leaderboards (members of the user's gyms always see it)
ALTER TABLE users
    ADD COLUMN public_leaderboard_name BOOLEAN NOT NULL DEFAULT FALSE;
