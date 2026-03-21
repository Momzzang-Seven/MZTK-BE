package momzzangseven.mztkbe.modules.post.application.port.out;

import java.util.List;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;

/**
 * Output port for delegating image updates to the image module. Implemented by the infrastructure
 * adapter that bridges the post and image modules.
 */
public interface UpdatePostImagesPort {
  /**
   * Updates images associated with a post. Called during post modification to synchronize the image
   * set (unlink removed images, reorder remaining ones).
   *
   * @param userId ID of the post owner (used for ownership validation)
   * @param postId ID of the post being modified
   * @param postType string representation of the post type (e.g., "FREE", "QUESTION")
   * @param imageIds ordered list of image IDs to keep; empty list removes all images
   */
  void updateImages(Long userId, Long postId, PostType postType, List<Long> imageIds);
}
