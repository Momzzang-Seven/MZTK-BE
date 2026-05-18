package momzzangseven.mztkbe.modules.web3.treasury.domain.event;

/**
 * Published after a {@code TreasuryWallet} row has transitioned from DISABLED → ACTIVE without
 * swapping its KMS key (ReEnableSameKey action, case C5). Drives the post-commit {@code
 * enableKey(kmsKeyId)} side effect; failures land in {@code web3_treasury_kms_audits}.
 *
 * @param walletAlias canonical alias bound to the row
 * @param kmsKeyId existing {@code kms_key_id} (unchanged by this action; now to be re-enabled in
 *     AWS KMS)
 * @param walletAddress {@code 0x}-prefixed address (unchanged by this action)
 * @param operatorUserId admin user id that triggered the re-activation
 */
public record TreasuryWalletReactivatedEvent(
    String walletAlias, String kmsKeyId, String walletAddress, Long operatorUserId) {}
