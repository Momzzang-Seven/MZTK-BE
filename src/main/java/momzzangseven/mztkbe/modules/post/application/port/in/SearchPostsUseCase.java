package momzzangseven.mztkbe.modules.post.application.port.in;

import java.util.List;
import momzzangseven.mztkbe.modules.post.application.dto.PostSearchCondition;
import momzzangseven.mztkbe.modules.post.domain.model.Post;

public interface SearchPostsUseCase {
  List<Post> searchPosts(PostSearchCondition condition);
}
