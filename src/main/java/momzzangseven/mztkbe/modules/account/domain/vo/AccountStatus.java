package momzzangseven.mztkbe.modules.account.domain.vo;

/** Represents the lifecycle status of a user account. */
public enum AccountStatus {
  /** Account is active and can be used for login. */
  ACTIVE,

  /** Account has been soft-deleted (withdrawn by the user). */
  DELETED,

  /** Account has been blocked by an administrator. */
  BLOCKED,

  /** Account has been created but email/identity is not yet verified. */
  UNVERIFIED
}
