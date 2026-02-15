package momzzangseven.mztkbe.modules.post.application.port.out;

import java.util.List;

public interface LinkTagPort {
  void linkTagsToPost(Long postId, List<String> tagNames);
}
