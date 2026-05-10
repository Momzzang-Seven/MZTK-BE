package momzzangseven.mztkbe.modules.comment.application.port.in;

import momzzangseven.mztkbe.modules.comment.application.dto.GetManagedBoardPostCommentsQuery;
import momzzangseven.mztkbe.modules.comment.application.dto.ManagedBoardCommentView;
import org.springframework.data.domain.Page;

/** Input port for admin board post comments. */
public interface GetManagedBoardPostCommentsUseCase {

  Page<ManagedBoardCommentView> execute(GetManagedBoardPostCommentsQuery query);
}
