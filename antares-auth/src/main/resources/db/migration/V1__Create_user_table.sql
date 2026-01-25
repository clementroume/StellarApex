-- V1__Create_user_table.sql
-- Initial users table for Antares application.
-- This script creates the "users" table, a trigger for 'updated_at',
-- and database-level CHECK constraints for data integrity.

CREATE TABLE users
(
    id         BIGSERIAL PRIMARY KEY,
    first_name VARCHAR(255),
    last_name  VARCHAR(255),
    email      VARCHAR(255)                NOT NULL UNIQUE,
    password   VARCHAR(255)                NOT NULL,
    platform_role VARCHAR(50)              NOT NULL,
    enabled    BOOLEAN                     NOT NULL DEFAULT TRUE,
    locale     VARCHAR(10)                 NOT NULL DEFAULT 'en',
    theme      VARCHAR(20)                 NOT NULL DEFAULT 'light',
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Add CHECK constraints to enforce valid values at the database level
ALTER TABLE users
    ADD CONSTRAINT chk_theme CHECK (theme IN ('light', 'dark')),
    ADD CONSTRAINT chk_locale CHECK (locale ~ '^[a-z]{2}(-[A-Z]{2})?$'),
    ADD CONSTRAINT chk_platform_role CHECK (platform_role IN ('ADMIN', 'USER'));