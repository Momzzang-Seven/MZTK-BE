package momzzangseven.mztkbe.modules.web3.treasury.domain.event;

/**
 * Published after a {@code TreasuryWallet} row has been UPDATE'd to point at a new KMS key
 * (ReplaceKey action, cases C6–C9). Drives the post-commit handler that
 *
 * <ul>
 *   <li>{@code updateAlias(walletAlias, newKmsKeyId)} — re-target the KMS alias.
 *   <li>If {@code disposeOldKey == true}: {@code disableKey(oldKmsKeyId)} + {@code
 *       scheduleKeyDeletion(oldKmsKeyId, 7)}. When the old key was already ARCHIVED in our domain
 *       it is already scheduled for deletion in KMS, so {@code disposeOldKey == false} to avoid
 *       double-scheduling.
 * </ul>
 *
 * <p>Failures land in {@code web3_treasury_kms_audits} for operator follow-up.
 *
 * @param walletAlias canonical alias bound to the row
 * @param oldKmsKeyId previous {@code kms_key_id} (still referenced by the KMS alias at the moment
 *     of publishing)
 * @param newKmsKeyId freshly imported {@code kms_key_id} now bound to the row
 * @param walletAddress {@code 0x}-prefixed address now stored on the row (may equal or differ from
 *     the previous value)
 * @param operatorUserId admin user id that triggered the rotation
 * @param disposeOldKey whether the post-commit handler should disable + schedule deletion of {@code
 *     oldKmsKeyId} (true for C7/C8; false for C6/C9 where the old key is already pending deletion)
 */
public record TreasuryWalletKeyReplacedEvent(
    String walletAlias,
    String oldKmsKeyId,
    String newKmsKeyId,
    String walletAddress,
    Long operatorUserId,
    boolean disposeOldKey) {}
