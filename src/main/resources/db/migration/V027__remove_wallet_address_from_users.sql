-- Remove the legacy wallet_address column from users table.
-- Wallet data is managed exclusively in user_wallets.
ALTER TABLE users DROP COLUMN wallet_address;
