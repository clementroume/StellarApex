-- V1__Create_user_table.sql
-- Initial users table for Antares application.
-- This script creates the "users" table and a trigger to update the updated_at timestamp on row updates.

CREATE TABLE users
(
    id         BIGSERIAL PRIMARY KEY,
    first_name VARCHAR(255),
    last_name  VARCHAR(255),
    email      VARCHAR(255)                NOT NULL UNIQUE,
    password   VARCHAR(255)                NOT NULL,
    role       VARCHAR(50)                 NOT NULL,
    enabled    BOOLEAN                     NOT NULL DEFAULT TRUE,
    locale     VARCHAR(10)                 NOT NULL DEFAULT 'en',
    theme      VARCHAR(20)                 NOT NULL DEFAULT 'light',
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Trigger function to update updated_at on row update
CREATE OR REPLACE FUNCTION update_updated_at_column()
    RETURNS TRIGGER AS
$$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Trigger attached to "users" table
DROP TRIGGER IF EXISTS set_updated_at ON users;
CREATE TRIGGER set_updated_at
    BEFORE UPDATE
    ON users
    FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();

-- Add CHECK constraints to enforce valid values at the database level
ALTER TABLE users
    ADD CONSTRAINT chk_theme CHECK (theme IN ('light', 'dark')),
    ADD CONSTRAINT chk_locale CHECK (locale ~ '^[a-z]{2}(-[A-Z]{2})?$'),
    ADD CONSTRAINT chk_role CHECK (role IN ('ROLE_USER', 'ROLE_ADMIN'));

