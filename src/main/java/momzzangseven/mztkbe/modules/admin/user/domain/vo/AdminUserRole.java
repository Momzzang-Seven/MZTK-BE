package momzzangseven.mztkbe.modules.admin.user.domain.vo;

/** Admin-owned user role for admin application boundaries. */
public enum AdminUserRole {
  USER,
  TRAINER,
  ADMIN,
  ADMIN_SEED,
  ADMIN_GENERATED;

  public boolean isAdmin() {
    return this == ADMIN || this == ADMIN_SEED || this == ADMIN_GENERATED;
  }
}
