package momzzangseven.mztkbe.modules.account.application.port.out;

/** Output port for checking whether a refresh-token subject is an active admin account. */
public interface CheckAdminRefreshSubjectPort {

  /** Returns {@code true} when the given user ID owns an active admin account. */
  boolean isActiveAdmin(Long userId);
}
