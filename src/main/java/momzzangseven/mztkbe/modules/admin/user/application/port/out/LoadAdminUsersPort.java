package momzzangseven.mztkbe.modules.admin.user.application.port.out;

import java.time.Instant;
import java.util.Set;
import momzzangseven.mztkbe.modules.admin.user.application.dto.AdminUserRoleFilter;
import momzzangseven.mztkbe.modules.admin.user.application.dto.AdminUserSortKey;
import momzzangseven.mztkbe.modules.user.domain.model.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.lang.Nullable;

/** Output port for loading the admin user-management list. */
public interface LoadAdminUsersPort {

  java.util.List<AdminUserProfileView> load(AdminUserProfileQuery query);

  Page<AdminUserProfileView> loadPage(AdminUserProfilePageQuery query);

  record AdminUserProfileQuery(
      String search, AdminUserRoleFilter role, @Nullable Set<Long> candidateUserIds) {}

  record AdminUserProfilePageQuery(
      String search,
      AdminUserRoleFilter role,
      @Nullable Set<Long> candidateUserIds,
      int page,
      int size,
      AdminUserSortKey sortKey) {}

  record AdminUserProfileView(
      Long userId, String nickname, UserRole role, String email, Instant joinedAt) {}
}
