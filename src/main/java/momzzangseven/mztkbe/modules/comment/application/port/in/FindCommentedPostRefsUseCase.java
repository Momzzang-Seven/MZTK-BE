package momzzangseven.mztkbe.modules.comment.application.port.in;

import java.util.List;
import momzzangseven.mztkbe.modules.comment.application.dto.FindCommentedPostRefsQuery;
import momzzangseven.mztkbe.modules.comment.application.dto.LatestCommentedPostRef;

public interface FindCommentedPostRefsUseCase {

  List<LatestCommentedPostRef> execute(FindCommentedPostRefsQuery query);
}
