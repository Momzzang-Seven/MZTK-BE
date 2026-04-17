package momzzangseven.mztkbe.modules.post.application.port.in;

import momzzangseven.mztkbe.modules.post.application.dto.PostSearchCondition;
import momzzangseven.mztkbe.modules.post.application.dto.SearchPostsResult;

public interface SearchPostsUseCase {
  SearchPostsResult searchPosts(PostSearchCondition condition, Long requesterUserId);
}
