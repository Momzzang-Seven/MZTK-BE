package momzzangseven.mztkbe.modules.post.application.port.in;

import momzzangseven.mztkbe.modules.post.application.dto.GetMyPostsCursorCommand;
import momzzangseven.mztkbe.modules.post.application.dto.GetMyPostsCursorResult;

public interface GetMyPostsCursorUseCase {

  GetMyPostsCursorResult execute(GetMyPostsCursorCommand command);
}
