package momzzangseven.mztkbe.modules.post.infrastructure.external.image.adapter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.image.application.dto.GetImagesByReferenceCommand;
import momzzangseven.mztkbe.modules.image.application.dto.GetImagesByReferenceResult;
import momzzangseven.mztkbe.modules.image.application.dto.GetImagesByReferencesCommand;
import momzzangseven.mztkbe.modules.image.application.dto.GetImagesByReferencesResult;
import momzzangseven.mztkbe.modules.image.application.dto.UpsertImagesByReferenceCommand;
import momzzangseven.mztkbe.modules.image.application.dto.ValidatePostAttachableImagesCommand;
import momzzangseven.mztkbe.modules.image.application.port.in.GetImagesByReferenceUseCase;
import momzzangseven.mztkbe.modules.image.application.port.in.GetImagesByReferencesUseCase;
import momzzangseven.mztkbe.modules.image.application.port.in.UpsertImagesByReferenceUseCase;
import momzzangseven.mztkbe.modules.image.application.port.in.ValidatePostAttachableImagesUseCase;
import momzzangseven.mztkbe.modules.image.domain.vo.ImageReferenceType;
import momzzangseven.mztkbe.modules.post.application.dto.PostImageResult;
import momzzangseven.mztkbe.modules.post.application.dto.PostImageResult.PostImageSlot;
import momzzangseven.mztkbe.modules.post.application.port.out.LoadPostImagesPort;
import momzzangseven.mztkbe.modules.post.application.port.out.UpdatePostImagesPort;
import momzzangseven.mztkbe.modules.post.application.port.out.ValidatePostImagesPort;
import momzzangseven.mztkbe.modules.post.domain.model.PostType;
import momzzangseven.mztkbe.modules.post.infrastructure.external.image.config.PostImageStorageProperties;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ImageModuleAdapter
    implements UpdatePostImagesPort, LoadPostImagesPort, ValidatePostImagesPort {

  private final UpsertImagesByReferenceUseCase upsertImagesByReferenceUseCase;
  private final GetImagesByReferenceUseCase getImagesByReferenceUseCase;
  private final GetImagesByReferencesUseCase getImagesByReferencesUseCase;
  private final ValidatePostAttachableImagesUseCase validatePostAttachableImagesUseCase;
  private final PostImageStorageProperties postImageStorageProperties;

  @Override
  public void updateImages(Long userId, Long postId, PostType postType, List<Long> imageIds) {
    ImageReferenceType refType = resolveReferenceType(postType);
    upsertImagesByReferenceUseCase.execute(
        new UpsertImagesByReferenceCommand(userId, postId, refType, imageIds));
  }

  @Override
  public void validateAttachableImages(
      Long userId, Long postId, PostType postType, List<Long> imageIds) {
    ImageReferenceType refType = resolveReferenceType(postType);
    validatePostAttachableImagesUseCase.execute(
        new ValidatePostAttachableImagesCommand(userId, postId, refType, imageIds));
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

  @Override
  public Map<Long, PostImageResult> loadImagesByPostIds(Map<PostType, List<Long>> postIdsByType) {
    if (postIdsByType == null || postIdsByType.isEmpty()) {
      return Map.of();
    }

    Map<Long, PostImageResult> merged = new HashMap<>();
    // For each type and ids in postIdsByType, find related images for each id in ids. If ids is
    // empty, return and check next PostType.
    postIdsByType.forEach(
        (postType, postIds) -> {
          if (postIds == null || postIds.isEmpty()) {
            return;
          }
          ImageReferenceType refType = resolveReferenceType(postType);
          GetImagesByReferencesResult result =
              getImagesByReferencesUseCase.execute(
                  new GetImagesByReferencesCommand(refType, postIds.stream().distinct().toList()));

          result
              .itemsByReferenceId()
              .forEach(
                  (postId, items) -> {
                    List<PostImageSlot> slots =
                        items.stream()
                            .map(
                                item ->
                                    new PostImageSlot(
                                        item.imageId(), buildImageUrl(item.finalObjectKey())))
                            .toList();
                    merged.put(postId, new PostImageResult(slots));
                  });
        });
    return merged;
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
      case FREE -> ImageReferenceType.COMMUNITY_FREE;
      case QUESTION -> ImageReferenceType.COMMUNITY_QUESTION;
      default ->
          throw new IllegalArgumentException("Unsupported postType for image ref: " + postType);
    };
  }
}
