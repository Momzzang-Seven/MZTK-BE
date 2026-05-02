package momzzangseven.mztkbe.global.audit.domain.vo;

/**
 * Target entity classification of an admin action audit record.
 *
 * <p>Persisted as the {@code target_type} column of {@code admin_action_audits} via {@link
 * Enum#name()}. Add a new value here when a new {@code @AdminOnly} call site needs to record
 * actions against a previously-unseen target entity type.
 */
public enum AuditTargetType {
  TREASURY_KEY,
  WEB3_TRANSACTION,
  ADMIN_ACCOUNT,
  QNA_ESCROW_QUESTION,
  USER_ACCOUNT,
  POST,
  COMMENT,
  DASHBOARD
}
