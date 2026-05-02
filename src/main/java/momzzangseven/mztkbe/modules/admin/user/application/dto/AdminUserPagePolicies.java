package momzzangseven.mztkbe.modules.admin.user.application.dto;

import java.util.Set;
import momzzangseven.mztkbe.modules.admin.common.application.dto.AdminPagePolicy;

public final class AdminUserPagePolicies {

  private AdminUserPagePolicies() {}

  public static final AdminPagePolicy USERS =
      new AdminPagePolicy(
          0,
          20,
          100,
          Set.of("joinedAt", "userId", "nickname", "role", "status", "postCount", "commentCount"));
}
