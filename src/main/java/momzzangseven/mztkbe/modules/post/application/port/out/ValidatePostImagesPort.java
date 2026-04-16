package momzzangseven.mztkbe.modules.post.application.port.out;

import java.util.List;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;

public interface ValidatePostImagesPort {
  void validateAttachableImages(Long userId, Long postId, PostType postType, List<Long> imageIds);
}
