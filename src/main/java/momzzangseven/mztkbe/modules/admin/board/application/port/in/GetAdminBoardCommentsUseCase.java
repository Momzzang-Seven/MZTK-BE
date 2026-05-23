package momzzangseven.mztkbe.modules.admin.board.application.port.in;

import momzzangseven.mztkbe.modules.admin.board.application.dto.AdminBoardCommentSearchResult;
import momzzangseven.mztkbe.modules.admin.board.application.dto.GetAdminBoardCommentsCommand;
import org.springframework.data.domain.Page;

/** Input port for admin board global comment search reads. */
public interface GetAdminBoardCommentsUseCase {

  Page<AdminBoardCommentSearchResult> execute(GetAdminBoardCommentsCommand command);
}
