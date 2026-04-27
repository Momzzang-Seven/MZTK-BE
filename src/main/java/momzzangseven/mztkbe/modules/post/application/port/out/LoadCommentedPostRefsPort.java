package momzzangseven.mztkbe.modules.post.application.port.out;

import java.util.List;
import momzzangseven.mztkbe.global.pagination.CursorPageRequest;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;

public interface LoadCommentedPostRefsPort {

  List<CommentedPostRef> loadCommentedPostRefs(
      Long requesterId, PostType type, CursorPageRequest pageRequest);
}
