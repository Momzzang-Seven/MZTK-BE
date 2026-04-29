package momzzangseven.mztkbe.modules.web3.treasury.domain.event;

/**
 * Published after a {@code TreasuryWallet} has transitioned to {@code ARCHIVED} and the row has
 * committed. Drives the post-commit KMS {@code ScheduleKeyDeletion} side effect; failures land in
 * {@code web3_treasury_kms_audits} for operator follow-up.
 */
public record TreasuryWalletArchivedEvent(
    String walletAlias,
    String kmsKeyId,
    String walletAddress,
    Long operatorUserId,
    int pendingWindowDays) {}
