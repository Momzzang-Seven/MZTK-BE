package momzzangseven.mztkbe.modules.comment.application.port.out;

import momzzangseven.mztkbe.modules.comment.application.dto.GetManagedBoardPostCommentsQuery;
import momzzangseven.mztkbe.modules.comment.application.dto.ManagedBoardCommentView;
import org.springframework.data.domain.Page;

/** Output port for loading admin board post comments. */
public interface LoadManagedBoardPostCommentsPort {

  Page<ManagedBoardCommentView> load(GetManagedBoardPostCommentsQuery query);
}
