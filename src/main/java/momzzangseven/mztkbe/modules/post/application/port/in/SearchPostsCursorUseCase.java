package momzzangseven.mztkbe.modules.post.application.port.in;

import momzzangseven.mztkbe.modules.post.application.dto.PostCursorSearchCondition;
import momzzangseven.mztkbe.modules.post.application.dto.SearchPostsCursorResult;

public interface SearchPostsCursorUseCase {
  SearchPostsCursorResult searchPostsByCursor(
      PostCursorSearchCondition condition, Long requesterUserId);
}
