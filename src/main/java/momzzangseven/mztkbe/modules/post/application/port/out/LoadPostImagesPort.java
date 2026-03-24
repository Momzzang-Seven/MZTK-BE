package momzzangseven.mztkbe.modules.post.application.port.out;

import momzzangseven.mztkbe.modules.post.application.dto.PostImageResult;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;

public interface LoadPostImagesPort {

  PostImageResult loadImages(PostType postType, Long postId);
}
