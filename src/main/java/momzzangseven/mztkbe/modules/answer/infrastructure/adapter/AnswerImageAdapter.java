package momzzangseven.mztkbe.modules.answer.infrastructure.adapter;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.answer.application.dto.AnswerImageResult;
import momzzangseven.mztkbe.modules.answer.application.dto.AnswerImageResult.AnswerImageSlot;
import momzzangseven.mztkbe.modules.answer.application.port.out.LoadAnswerImagesPort;
import momzzangseven.mztkbe.modules.answer.application.port.out.UpdateAnswerImagesPort;
import momzzangseven.mztkbe.modules.answer.infrastructure.config.AnswerImageStorageProperties;
import momzzangseven.mztkbe.modules.image.application.dto.GetImagesByReferencesCommand;
import momzzangseven.mztkbe.modules.image.application.dto.GetImagesByReferencesResult;
import momzzangseven.mztkbe.modules.image.application.dto.UpsertImagesByReferenceCommand;
import momzzangseven.mztkbe.modules.image.application.port.in.GetImagesByReferencesUseCase;
import momzzangseven.mztkbe.modules.image.application.port.in.UpsertImagesByReferenceUseCase;
import momzzangseven.mztkbe.modules.image.domain.vo.ImageReferenceType;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AnswerImageAdapter implements UpdateAnswerImagesPort, LoadAnswerImagesPort {

  private final UpsertImagesByReferenceUseCase upsertImagesByReferenceUseCase;
  private final GetImagesByReferencesUseCase getImagesByReferencesUseCase;
  private final AnswerImageStorageProperties answerImageStorageProperties;

  @Override
  public void updateImages(Long userId, Long answerId, List<Long> imageIds) {
    upsertImagesByReferenceUseCase.execute(
        new UpsertImagesByReferenceCommand(
            userId, answerId, ImageReferenceType.COMMUNITY_ANSWER, imageIds));
  }

  @Override
  public Map<Long, AnswerImageResult> loadImagesByAnswerIds(Collection<Long> answerIds) {
    if (answerIds == null || answerIds.isEmpty()) {
      return Map.of();
    }

    GetImagesByReferencesResult result =
        getImagesByReferencesUseCase.execute(
            new GetImagesByReferencesCommand(
                ImageReferenceType.COMMUNITY_ANSWER, answerIds.stream().toList()));

    return result.itemsByReferenceId().entrySet().stream()
        .collect(
            Collectors.toMap(
                Map.Entry::getKey,
                entry ->
                    new AnswerImageResult(
                        entry.getValue().stream()
                            .map(
                                item ->
                                    new AnswerImageSlot(
                                        item.imageId(), buildImageUrl(item.finalObjectKey())))
                            .toList())));
  }

  private String buildImageUrl(String finalObjectKey) {
    if (finalObjectKey == null || finalObjectKey.isBlank()) {
      return null;
    }
    return normalizeUrlPrefix(answerImageStorageProperties.getUrlPrefix())
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
}
