-- Demo accounts (roadmap D68): the seeded accounts stay available as public demo accounts with a
-- known password, so they must never hold platform privileges. The flag is set by the seeder; the
-- CHECK is the last line of defence below the application rules (User, GatewayIdentityService).
ALTER TABLE users
    ADD COLUMN demo BOOLEAN NOT NULL DEFAULT FALSE,
    ADD CONSTRAINT chk_users_demo_not_admin CHECK (NOT demo OR platform_role <> 'ADMIN');
