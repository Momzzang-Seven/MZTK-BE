package momzzangseven.mztkbe.modules.post.application.port.in;

import java.util.List;
import momzzangseven.mztkbe.modules.post.application.dto.PostListResult;
import momzzangseven.mztkbe.modules.post.application.dto.PostSearchCondition;

public interface SearchPostsUseCase {
  List<PostListResult> searchPosts(PostSearchCondition condition);
}
