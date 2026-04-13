-- Add ADMIN_SEED and ADMIN_GENERATED to users.role check constraint
ALTER TABLE users DROP CONSTRAINT users_role_check;
ALTER TABLE users ADD CONSTRAINT users_role_check
    CHECK (role IN ('USER', 'TRAINER', 'ADMIN', 'ADMIN_SEED', 'ADMIN_GENERATED'));
