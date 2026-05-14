package momzzangseven.mztkbe.modules.web3.treasury.domain.event;

/**
 * Published once per wallet alias after its row has been persisted with a {@code kms_key_id}.
 * Triggers creation of one alias-level row in {@code web3_treasury_provision_audits}. For a cohort
 * of N sibling aliases sharing one key, this event fires N times — once per alias.
 *
 * <p>{@code coBind = true} signals the alias was bound to an existing cohort's shared key (no new
 * KMS key was created). {@code aliasRepairMode = true} signals the row already existed (operator
 * retry of a prior run that committed the row but left the KMS alias missing or stale). Both flags
 * are recorded so dashboards can distinguish first-time provisioning, co-bind, and alias repair.
 */
public record AliasProvisionedAuditEvent(
    String walletAlias,
    String walletAddress,
    Long operatorUserId,
    boolean coBind,
    boolean aliasRepairMode) {}
