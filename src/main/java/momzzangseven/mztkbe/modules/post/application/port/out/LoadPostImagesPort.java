package momzzangseven.mztkbe.modules.post.application.port.out;

import java.util.List;
import java.util.Map;
import momzzangseven.mztkbe.modules.post.application.dto.PostImageResult;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;

public interface LoadPostImagesPort {

  PostImageResult loadImages(PostType postType, Long postId);

  Map<Long, PostImageResult> loadImagesByPostIds(Map<PostType, List<Long>> postIdsByType);
}
