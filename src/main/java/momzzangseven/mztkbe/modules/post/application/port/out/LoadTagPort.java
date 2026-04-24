package momzzangseven.mztkbe.modules.post.application.port.out;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface LoadTagPort {
  Optional<Long> findTagIdByName(String tagName);

  List<Long> findPostIdsByTagName(String tagName);

  List<String> findTagNamesByPostId(Long postId);

  Map<Long, List<String>> findTagsByPostIdsIn(List<Long> postIds);
}
