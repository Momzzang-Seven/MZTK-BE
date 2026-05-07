package momzzangseven.mztkbe.modules.comment.application.port.in;

import java.util.List;
import java.util.Map;

/** Use case for counting managed board comments by root post ID. */
public interface CountManagedBoardPostCommentsUseCase {

  Map<Long, Long> countByPostIds(List<Long> postIds);
}
