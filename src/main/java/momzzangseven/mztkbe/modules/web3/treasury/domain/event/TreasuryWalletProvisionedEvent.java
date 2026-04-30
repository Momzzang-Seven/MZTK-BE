package momzzangseven.mztkbe.modules.web3.treasury.domain.event;

/**
 * Published after a {@code TreasuryWallet} row has been persisted with a {@code kms_key_id}. Drives
 * the post-commit KMS {@code CreateAlias} side effect; failures land in {@code
 * web3_treasury_kms_audits} for operator follow-up.
 *
 * <p>{@code aliasRepairMode = true} signals that the row already existed (operator retry of a prior
 * run that committed the row but left the alias missing or stale); the handler still binds
 * idempotently, but the audit row is logged so dashboards can distinguish first-time provisioning
 * from alias repair.
 */
public record TreasuryWalletProvisionedEvent(
    String walletAlias,
    String kmsKeyId,
    String walletAddress,
    Long operatorUserId,
    boolean aliasRepairMode) {}
