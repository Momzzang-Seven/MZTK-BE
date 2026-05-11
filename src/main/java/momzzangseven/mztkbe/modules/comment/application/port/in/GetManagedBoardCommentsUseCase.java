package momzzangseven.mztkbe.modules.comment.application.port.in;

import momzzangseven.mztkbe.modules.comment.application.dto.GetManagedBoardCommentsQuery;
import momzzangseven.mztkbe.modules.comment.application.dto.ManagedBoardCommentSearchView;
import org.springframework.data.domain.Page;

/** Input port for admin board global comment search reads. */
public interface GetManagedBoardCommentsUseCase {

  Page<ManagedBoardCommentSearchView> execute(GetManagedBoardCommentsQuery query);
}
