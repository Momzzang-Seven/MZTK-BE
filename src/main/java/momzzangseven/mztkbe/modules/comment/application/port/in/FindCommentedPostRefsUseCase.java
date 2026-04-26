package momzzangseven.mztkbe.modules.comment.application.port.in;

import java.util.List;
import momzzangseven.mztkbe.modules.comment.application.dto.CommentedPostRef;
import momzzangseven.mztkbe.modules.comment.application.dto.FindCommentedPostRefsQuery;

public interface FindCommentedPostRefsUseCase {

  List<CommentedPostRef> execute(FindCommentedPostRefsQuery query);
}
