package momzzangseven.mztkbe.modules.post.infrastructure.external.image.adapter;

import java.util.List;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.image.application.dto.GetImagesByReferenceCommand;
import momzzangseven.mztkbe.modules.image.application.dto.GetImagesByReferenceResult;
import momzzangseven.mztkbe.modules.image.application.dto.UpsertImagesByReferenceCommand;
import momzzangseven.mztkbe.modules.image.application.port.in.GetImagesByReferenceUseCase;
import momzzangseven.mztkbe.modules.image.application.port.in.UpsertImagesByReferenceUseCase;
import momzzangseven.mztkbe.modules.image.domain.vo.ImageReferenceType;
import momzzangseven.mztkbe.modules.post.application.dto.PostImageResult;
import momzzangseven.mztkbe.modules.post.application.dto.PostImageResult.PostImageSlot;
import momzzangseven.mztkbe.modules.post.application.port.out.LoadPostImagesPort;
import momzzangseven.mztkbe.modules.post.application.port.out.UpdatePostImagesPort;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import momzzangseven.mztkbe.modules.post.infrastructure.external.image.config.PostImageStorageProperties;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ImageModuleAdapter implements UpdatePostImagesPort, LoadPostImagesPort {

  private final UpsertImagesByReferenceUseCase upsertImagesByReferenceUseCase;
  private final GetImagesByReferenceUseCase getImagesByReferenceUseCase;
  private final PostImageStorageProperties postImageStorageProperties;

  @Override
  public void updateImages(Long userId, Long postId, PostType postType, List<Long> imageIds) {
    ImageReferenceType refType = resolveReferenceType(postType);
    upsertImagesByReferenceUseCase.execute(
        new UpsertImagesByReferenceCommand(userId, postId, refType, imageIds));
  }

  @Override
  public PostImageResult loadImages(PostType postType, Long postId) {
    ImageReferenceType refType = resolveReferenceType(postType);
    GetImagesByReferenceResult result =
        getImagesByReferenceUseCase.execute(new GetImagesByReferenceCommand(refType, postId));

    List<PostImageSlot> slots =
        result.items().stream()
            .map(item -> new PostImageSlot(item.imageId(), buildImageUrl(item.finalObjectKey())))
            .toList();

    return new PostImageResult(slots);
  }

  private String buildImageUrl(String finalObjectKey) {
    if (finalObjectKey == null || finalObjectKey.isBlank()) {
      return null;
    }
    return normalizeUrlPrefix(postImageStorageProperties.getUrlPrefix())
        + stripLeadingSlash(finalObjectKey);
  }

  private String normalizeUrlPrefix(String prefix) {
    if (prefix == null || prefix.isBlank()) {
      throw new IllegalStateException("cloud.aws.s3.url-prefix must not be blank");
    }
    return prefix.endsWith("/") ? prefix : prefix + "/";
  }

  private String stripLeadingSlash(String value) {
    return value.startsWith("/") ? value.substring(1) : value;
  }

  private ImageReferenceType resolveReferenceType(PostType postType) {
    return switch (postType) {
      case PostType.FREE -> ImageReferenceType.COMMUNITY_FREE;
      case PostType.QUESTION -> ImageReferenceType.COMMUNITY_QUESTION;
      default ->
          throw new IllegalArgumentException("Unsupported postType for image ref: " + postType);
    };
  }
}
