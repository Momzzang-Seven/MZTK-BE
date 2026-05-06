package momzzangseven.mztkbe.modules.admin.board.application.port.out;

import java.util.List;
import java.util.Map;

/** Output port for loading comment counts by post ID. */
public interface LoadAdminBoardPostCommentCountsPort {

  Map<Long, Long> load(List<Long> postIds);
}
