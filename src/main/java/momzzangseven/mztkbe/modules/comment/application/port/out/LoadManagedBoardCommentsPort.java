package momzzangseven.mztkbe.modules.comment.application.port.out;

import momzzangseven.mztkbe.modules.comment.application.dto.GetManagedBoardCommentsQuery;
import momzzangseven.mztkbe.modules.comment.application.dto.ManagedBoardCommentSearchView;
import org.springframework.data.domain.Page;

/** Output port for loading admin board global comment search rows. */
public interface LoadManagedBoardCommentsPort {

  Page<ManagedBoardCommentSearchView> load(GetManagedBoardCommentsQuery query);
}
