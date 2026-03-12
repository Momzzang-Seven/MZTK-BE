package momzzangseven.mztkbe.modules.post.application.port.in;

import java.util.List;
import momzzangseven.mztkbe.modules.post.application.dto.PostResult;
import momzzangseven.mztkbe.modules.post.application.dto.PostSearchCondition;

public interface SearchPostsUseCase {
  List<PostResult> searchPosts(PostSearchCondition condition);
}
