package momzzangseven.mztkbe.modules.image.application.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.image.ImageMaxCountExceedException;
import momzzangseven.mztkbe.global.error.image.ImageNotBelongsToUserException;
import momzzangseven.mztkbe.global.error.image.ImageNotFoundException;
import momzzangseven.mztkbe.global.error.image.InvalidImageRefTypeException;
import momzzangseven.mztkbe.modules.image.application.dto.ApplyAnswerUpdateImagesCommand;
import momzzangseven.mztkbe.modules.image.application.dto.ReleaseAnswerUpdateImagesCommand;
import momzzangseven.mztkbe.modules.image.application.dto.ReserveAnswerUpdateImagesCommand;
import momzzangseven.mztkbe.modules.image.application.port.in.ManageAnswerUpdateImagesUseCase;
import momzzangseven.mztkbe.modules.image.application.port.out.LoadImagePort;
import momzzangseven.mztkbe.modules.image.application.port.out.UpdateImagePort;
import momzzangseven.mztkbe.modules.image.domain.model.Image;
import momzzangseven.mztkbe.modules.image.domain.vo.ImageCountPolicy;
import momzzangseven.mztkbe.modules.image.domain.vo.ImageReferenceType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ManageAnswerUpdateImagesService implements ManageAnswerUpdateImagesUseCase {

  private static final ImageReferenceType COMMUNITY_ANSWER = ImageReferenceType.COMMUNITY_ANSWER;
  private static final ImageReferenceType COMMUNITY_ANSWER_UPDATE =
      ImageReferenceType.COMMUNITY_ANSWER_UPDATE;

  private final LoadImagePort loadImagePort;
  private final UpdateImagePort updateImagePort;

  @Override
  @Transactional
  public void reservePendingImages(ReserveAnswerUpdateImagesCommand command) {
    command.validate();
    validateImageIds(command.imageIds());
    if (command.imageIds().isEmpty()) {
      return;
    }
    Map<Long, Image> imagesById = loadImagesForUpdate(command.imageIds());
    validateAttachableImages(
        imagesById,
        command.imageIds(),
        command.userId(),
        command.answerId(),
        command.updateStateId());
    List<Image> reservableImages = new ArrayList<>();
    for (Long imageId : command.imageIds()) {
      Image image = imagesById.get(imageId);
      if (image.getReferenceType() == COMMUNITY_ANSWER && image.getReferenceId() == null) {
        reservableImages.add(
            image.toBuilder()
                .referenceType(COMMUNITY_ANSWER_UPDATE)
                .referenceId(command.updateStateId())
                .imgOrder(null)
                .build());
      }
    }
    if (!reservableImages.isEmpty()) {
      updateImagePort.updateAll(reservableImages);
    }
  }

  @Override
  @Transactional
  public void applyPendingImages(ApplyAnswerUpdateImagesCommand command) {
    command.validate();
    validateImageIds(command.imageIds());
    Map<Long, Image> requestedImagesById = loadImagesForUpdate(command.imageIds());
    validateAttachableImages(
        requestedImagesById,
        command.imageIds(),
        command.userId(),
        command.answerId(),
        command.updateStateId());

    Set<Long> requestedIds = new LinkedHashSet<>(command.imageIds());
    List<Image> currentImages =
        loadImagePort.findImagesByReferenceForUpdate(COMMUNITY_ANSWER.expand(), command.answerId());
    List<Image> updates = new ArrayList<>();
    for (Image image : currentImages) {
      if (!requestedIds.contains(image.getId())) {
        updates.add(
            image.toBuilder()
                .referenceType(COMMUNITY_ANSWER)
                .referenceId(null)
                .imgOrder(null)
                .build());
      }
    }
    for (int index = 0; index < command.imageIds().size(); index++) {
      Image image = requestedImagesById.get(command.imageIds().get(index));
      updates.add(
          image.updateReference(COMMUNITY_ANSWER, command.answerId()).updateImageOrder(index + 1));
    }
    if (!updates.isEmpty()) {
      updateImagePort.updateAll(updates);
    }
  }

  @Override
  @Transactional
  public void releasePendingImages(ReleaseAnswerUpdateImagesCommand command) {
    command.validate();
    if (command.updateStateIds().isEmpty()) {
      return;
    }
    List<Image> pendingImages =
        loadImagePort.findImagesByReferenceIds(
            COMMUNITY_ANSWER_UPDATE.expand(), command.updateStateIds());
    if (pendingImages.isEmpty()) {
      return;
    }
    updateImagePort.updateAll(
        pendingImages.stream()
            .map(
                image ->
                    image.toBuilder()
                        .referenceType(COMMUNITY_ANSWER)
                        .referenceId(null)
                        .imgOrder(null)
                        .build())
            .toList());
  }

  private void validateImageIds(List<Long> imageIds) {
    int maxCount = ImageCountPolicy.of(COMMUNITY_ANSWER).getMaxCount();
    if (imageIds.size() > maxCount) {
      throw new ImageMaxCountExceedException(
          "Image count " + imageIds.size() + " exceeds the limit of " + maxCount);
    }
    Set<Long> uniqueIds = new LinkedHashSet<>();
    for (Long imageId : imageIds) {
      if (imageId == null || imageId <= 0) {
        throw new IllegalArgumentException("imageIds must be positive");
      }
      if (!uniqueIds.add(imageId)) {
        throw new InvalidImageRefTypeException("Duplicate image id is not allowed");
      }
    }
  }

  private Map<Long, Image> loadImagesForUpdate(List<Long> imageIds) {
    if (imageIds.isEmpty()) {
      return Map.of();
    }
    return loadImagePort.findImagesByIdInForUpdate(imageIds).stream()
        .collect(
            LinkedHashMap::new,
            (map, image) -> map.put(image.getId(), image),
            LinkedHashMap::putAll);
  }

  private void validateAttachableImages(
      Map<Long, Image> imagesById,
      List<Long> imageIds,
      Long userId,
      Long answerId,
      Long updateStateId) {
    for (Long imageId : imageIds) {
      Image image = imagesById.get(imageId);
      if (image == null) {
        throw new ImageNotFoundException("Requested image not found: id=" + imageId);
      }
      if (!userId.equals(image.getUserId())) {
        throw new ImageNotBelongsToUserException("Image does not belong to user");
      }
      if (image.getReferenceType() == COMMUNITY_ANSWER) {
        if (image.getReferenceId() != null && !answerId.equals(image.getReferenceId())) {
          throw new InvalidImageRefTypeException("Image is already linked to a different entity");
        }
        continue;
      }
      if (image.getReferenceType() == COMMUNITY_ANSWER_UPDATE
          && updateStateId.equals(image.getReferenceId())) {
        continue;
      }
      throw new InvalidImageRefTypeException("New image has different reference type with command");
    }
  }
}
