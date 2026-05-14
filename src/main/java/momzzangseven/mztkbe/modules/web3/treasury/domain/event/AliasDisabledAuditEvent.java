package momzzangseven.mztkbe.modules.web3.treasury.domain.event;

/**
 * Published once per wallet alias after its cohort has transitioned to {@code DISABLED} and the
 * rows have committed. Triggers one alias-level row in {@code web3_treasury_provision_audits}. For
 * a cohort of N sibling aliases this event fires N times — once per alias — while the KMS {@code
 * DisableKey} side effect fires once per cohort.
 */
public record AliasDisabledAuditEvent(
    String walletAlias, String walletAddress, Long operatorUserId) {}
