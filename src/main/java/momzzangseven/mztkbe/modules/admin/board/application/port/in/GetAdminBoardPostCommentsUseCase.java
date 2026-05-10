package momzzangseven.mztkbe.modules.admin.board.application.port.in;

import momzzangseven.mztkbe.modules.admin.board.application.dto.AdminBoardCommentResult;
import momzzangseven.mztkbe.modules.admin.board.application.dto.GetAdminBoardPostCommentsCommand;
import org.springframework.data.domain.Page;

/** Input port for admin board post comment reads. */
public interface GetAdminBoardPostCommentsUseCase {

  Page<AdminBoardCommentResult> execute(GetAdminBoardPostCommentsCommand command);
}
