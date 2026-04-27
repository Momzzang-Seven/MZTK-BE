package momzzangseven.mztkbe.modules.post.application.port.in;

import momzzangseven.mztkbe.modules.post.application.dto.GetMyCommentedPostsCursorCommand;
import momzzangseven.mztkbe.modules.post.application.dto.GetMyCommentedPostsCursorResult;

public interface GetMyCommentedPostsCursorUseCase {

  GetMyCommentedPostsCursorResult execute(GetMyCommentedPostsCursorCommand command);
}
