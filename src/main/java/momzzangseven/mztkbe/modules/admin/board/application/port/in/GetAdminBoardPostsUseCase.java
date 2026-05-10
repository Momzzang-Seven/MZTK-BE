package momzzangseven.mztkbe.modules.admin.board.application.port.in;

import momzzangseven.mztkbe.modules.admin.board.application.dto.AdminBoardPostResult;
import momzzangseven.mztkbe.modules.admin.board.application.dto.GetAdminBoardPostsCommand;
import org.springframework.data.domain.Page;

/** Input port for admin board post list reads. */
public interface GetAdminBoardPostsUseCase {

  Page<AdminBoardPostResult> execute(GetAdminBoardPostsCommand command);
}
