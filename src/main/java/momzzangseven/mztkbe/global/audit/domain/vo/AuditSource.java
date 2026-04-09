package momzzangseven.mztkbe.global.audit.domain.vo;

/**
 * Source module of an admin action audit record.
 *
 * <p>Persisted as the {@code source} column of {@code admin_action_audits}. Add a new value here
 * when a new module records admin actions via {@code @AdminOnly}.
 */
public enum AuditSource {
  USER,
  WEB3
}
