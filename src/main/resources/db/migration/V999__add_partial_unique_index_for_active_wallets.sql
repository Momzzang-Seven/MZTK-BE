-- Drop old unique constraint (Hibernate-generated name)
ALTER TABLE user_wallets DROP CONSTRAINT IF EXISTS ukoflkrojfurl52oj0xjfn0j5ma;
ALTER TABLE user_wallets DROP CONSTRAINT IF EXISTS uk_wallet_address;

-- Create partial unique index for ACTIVE wallets only
-- This allows the same wallet_address to be reused after deactivation
CREATE UNIQUE INDEX IF NOT EXISTS unique_active_wallet_address 
ON user_wallets (wallet_address) 
WHERE status = 'ACTIVE';

-- Add comment for documentation
COMMENT ON INDEX unique_active_wallet_address IS 
'Ensures each wallet address can only have one ACTIVE record, but allows DEACTIVATED records to be reused';
