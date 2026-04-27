package momzzangseven.mztkbe.modules.post.application.port.in;

import momzzangseven.mztkbe.modules.post.application.dto.GetMyLikedPostsCursorCommand;
import momzzangseven.mztkbe.modules.post.application.dto.GetMyLikedPostsCursorResult;

public interface GetMyLikedPostsCursorUseCase {

  GetMyLikedPostsCursorResult execute(GetMyLikedPostsCursorCommand command);
}
