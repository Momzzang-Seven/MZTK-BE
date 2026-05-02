package momzzangseven.mztkbe.modules.admin.board.application.dto;

import java.util.Set;
import momzzangseven.mztkbe.modules.admin.common.application.dto.AdminPagePolicy;

public final class AdminBoardPagePolicies {

  private AdminBoardPagePolicies() {}

  public static final AdminPagePolicy POSTS =
      new AdminPagePolicy(
          0, 20, 100, Set.of("createdAt", "postId", "status", "type", "commentCount"));

  public static final AdminPagePolicy POST_COMMENTS =
      new AdminPagePolicy(0, 20, 100, Set.of("createdAt", "commentId"));
}
