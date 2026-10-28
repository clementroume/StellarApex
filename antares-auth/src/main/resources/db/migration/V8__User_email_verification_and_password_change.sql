-- E-mail verification (lot H): new accounts start unverified and confirm their address by a mailed
-- link. Accounts created before this migration (seed, test and early accounts) count as verified.
ALTER TABLE users
    ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE users
SET email_verified = TRUE;

-- Last password change (reset or change): access tokens issued before it are refused
ALTER TABLE users
    ADD COLUMN password_changed_at TIMESTAMP WITH TIME ZONE;
