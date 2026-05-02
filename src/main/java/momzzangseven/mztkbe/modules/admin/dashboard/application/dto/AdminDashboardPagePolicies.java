package momzzangseven.mztkbe.modules.admin.dashboard.application.dto;

import java.util.Set;
import momzzangseven.mztkbe.modules.admin.common.application.dto.AdminPagePolicy;

public final class AdminDashboardPagePolicies {

  private AdminDashboardPagePolicies() {}

  public static final AdminPagePolicy POST_STATS =
      new AdminPagePolicy(0, 20, 100, Set.of("createdAt"));
}
