package momzzangseven.mztkbe.modules.image.infrastructure.persistence.adapter;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.image.application.port.out.DeleteImagePort;
import momzzangseven.mztkbe.modules.image.application.port.out.LoadImagePort;
import momzzangseven.mztkbe.modules.image.application.port.out.SaveImagePort;
import momzzangseven.mztkbe.modules.image.application.port.out.UpdateImagePort;
import momzzangseven.mztkbe.modules.image.domain.model.Image;
import momzzangseven.mztkbe.modules.image.domain.vo.ImageReferenceType;
import momzzangseven.mztkbe.modules.image.domain.vo.ImageStatus;
import momzzangseven.mztkbe.modules.image.infrastructure.persistence.entity.ImageEntity;
import momzzangseven.mztkbe.modules.image.infrastructure.persistence.repository.ImageJpaRepository;
import org.springframework.stereotype.Component;

/**
 * Adapts persistence operations for the image module. Handles entity <-> domain model conversion
 * internally.
 */
@Component
@RequiredArgsConstructor
public class ImagePersistenceAdapter
    implements SaveImagePort, DeleteImagePort, LoadImagePort, UpdateImagePort {
  private final ImageJpaRepository imageJpaRepository;

  // ========== SaveImagePort Implementation ==========

  @Override
  public List<Image> saveAll(List<Image> images) {
    List<ImageEntity> entities = images.stream().map(this::toEntity).toList();
    return imageJpaRepository.saveAll(entities).stream().map(this::toDomain).toList();
  }

  // ========== DeleteImagePort Implementation ==========

  @Override
  public int deletePendingImagesBefore(Instant cutoff, int batchSize) {
    return imageJpaRepository.deletePendingBefore(cutoff, batchSize);
  }

  @Override
  public void unlinkImagesByReference(ImageReferenceType referenceType, Long referenceId) {
    imageJpaRepository.unlinkByReferenceTypeAndReferenceId(referenceType.name(), referenceId);
  }

  @Override
  public void unlinkImagesByIdIn(List<Long> ids) {
    if (ids.isEmpty()) {
      return;
    }
    imageJpaRepository.unlinkByIdIn(ids);
  }

  @Override
  public void deleteImagesByIdIn(List<Long> ids) {
    if (ids.isEmpty()) {
      return;
    }
    imageJpaRepository.deleteByIdIn(ids);
  }

  // ========== LoadImagePort Implementation ==========

  @Override
  public Optional<Image> findByTmpObjectKey(String tmpObjectKey) {
    return imageJpaRepository.findByTmpObjectKey(tmpObjectKey).map(this::toDomain);
  }

  @Override
  public Optional<Image> findByTmpObjectKeyForUpdate(String tmpObjectKey) {
    return imageJpaRepository.findByTmpObjectKeyForUpdate(tmpObjectKey).map(this::toDomain);
  }

  @Override
  public List<Image> findImagesByReference(ImageReferenceType referenceType, Long referenceId) {
    return imageJpaRepository
        .findAllByReferenceTypeAndReferenceId(referenceType.name(), referenceId)
        .stream()
        .map(this::toDomain)
        .toList();
  }

  @Override
  public List<Image> findImagesByIdIn(List<Long> ids) {
    return imageJpaRepository.findAllByIdIn(ids).stream().map(this::toDomain).toList();
  }

  @Override
  public List<Image> findImagesByIdInForUpdate(List<Long> ids) {
    return imageJpaRepository.findAllByIdInForUpdate(ids).stream().map(this::toDomain).toList();
  }

  @Override
  public List<Image> findUnlinkedImagesBefore(Instant cutoff, int batchSize) {
    return imageJpaRepository.findUnlinkedBefore(cutoff, batchSize).stream()
        .map(this::toDomain)
        .toList();
  }

  // ========== UpdateImagePort Implementation ==========

  @Override
  public Image update(Image image) {
    imageJpaRepository.updateStatusFinalKeyAndErrorReason(
        image.getId(), image.getStatus().name(), image.getFinalObjectKey(), image.getErrorReason());

    return imageJpaRepository
        .findById(image.getId())
        .map(this::toDomain)
        .orElseThrow(
            () -> new IllegalStateException("Image not found after update: " + image.getId()));
  }

  @Override
  public List<Image> updateAll(List<Image> images) {
    List<ImageEntity> entities = images.stream().map(this::toEntity).toList();
    return imageJpaRepository.saveAll(entities).stream().map(this::toDomain).toList();
  }

  /**
   * Helper method converting domain model to entity. Includes the entity ID so that saveAll()
   * performs UPDATE rather than INSERT when called from updateAll(). referenceType may be null for
   * unlinked images; handled safely via null check
   *
   * @param domain Domain model
   * @return Entity
   * @return
   */
  private ImageEntity toEntity(Image domain) {
    return ImageEntity.builder()
        .id(domain.getId())
        .userId(domain.getUserId())
        .referenceType(domain.getReferenceType() != null ? domain.getReferenceType().name() : null)
        .referenceId(domain.getReferenceId())
        .status(domain.getStatus().name())
        .tmpObjectKey(domain.getTmpObjectKey())
        .finalObjectKey(domain.getFinalObjectKey())
        .imgOrder(domain.getImgOrder())
        .errorReason(domain.getErrorReason())
        .build();
  }

  /**
   * Helper method converting entity to domain model. referenceType may be null for unlinked images;
   * handled safely via null check.
   *
   * @param entity Entity
   * @return Domain model
   */
  private Image toDomain(ImageEntity entity) {
    return Image.builder()
        .id(entity.getId())
        .userId(entity.getUserId())
        .referenceType(
            entity.getReferenceType() != null
                ? ImageReferenceType.valueOf(entity.getReferenceType())
                : null)
        .referenceId(entity.getReferenceId())
        .status(ImageStatus.valueOf(entity.getStatus()))
        .tmpObjectKey(entity.getTmpObjectKey())
        .finalObjectKey(entity.getFinalObjectKey())
        .imgOrder(entity.getImgOrder())
        .errorReason(entity.getErrorReason())
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .build();
  }
}
