package momzzangseven.mztkbe.modules.post.infrastructure.external.image.adapter;

import java.util.List;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.image.application.dto.UpdatePostImagesCommand;
import momzzangseven.mztkbe.modules.image.application.port.in.UpdatePostImagesUseCase;
import momzzangseven.mztkbe.modules.image.domain.vo.ImageReferenceType;
import momzzangseven.mztkbe.modules.post.application.port.out.UpdatePostImagesPort;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ImageModuleAdapter implements UpdatePostImagesPort {

  private final UpdatePostImagesUseCase updatePostImagesUseCase;

  @Override
  public void updateImages(Long userId, Long postId, PostType postType, List<Long> imageIds) {
    ImageReferenceType refType = resolveReferenceType(postType);
    updatePostImagesUseCase.execute(new UpdatePostImagesCommand(userId, postId, refType, imageIds));
  }

  /**
   * Maps a post type string to the corresponding {@link ImageReferenceType}. Kept here (not in
   * PostProcessService) so that post.application has no knowledge of image module internals.
   */
  private ImageReferenceType resolveReferenceType(PostType postType) {
    return switch (postType) {
      case PostType.FREE -> ImageReferenceType.COMMUNITY_FREE;
      case PostType.QUESTION -> ImageReferenceType.COMMUNITY_QUESTION;
      default ->
          throw new IllegalArgumentException("Unsupported postType for image ref: " + postType);
    };
  }
}
