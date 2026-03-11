package momzzangseven.mztkbe.modules.image.infrastructure.persistence.adaptor;

import java.util.List;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.image.application.port.out.SaveImagePort;
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
public class ImagePersistenceAdapter implements SaveImagePort {
  private final ImageJpaRepository imageJpaRepository;

  @Override
  public List<Image> saveAll(List<Image> images) {
    List<ImageEntity> entities = images.stream().map(this::toEntity).toList();
    return imageJpaRepository.saveAll(entities).stream().map(this::toDomain).toList();
  }

  /**
   * Helper method converting domain model to entity.
   *
   * @param domain Domain model
   * @return Entity
   * @return
   */
  private ImageEntity toEntity(Image domain) {
    return ImageEntity.builder()
        .userId(domain.getUserId())
        .referenceType(domain.getReferenceType().name())
        .referenceId(domain.getReferenceId())
        .status(domain.getStatus().name())
        .tmpObjectKey(domain.getTmpObjectKey())
        .finalObjectKey(domain.getFinalObjectKey())
        .imgOrder(domain.getImgOrder())
        .build();
  }

  /**
   * Helper method converting entity to domain model.
   *
   * @param entity Entity
   * @return Domain model
   */
  private Image toDomain(ImageEntity entity) {
    return Image.builder()
        .id(entity.getId())
        .userId(entity.getUserId())
        .referenceType(ImageReferenceType.valueOf(entity.getReferenceType()))
        .referenceId(entity.getReferenceId())
        .status(ImageStatus.valueOf(entity.getStatus()))
        .tmpObjectKey(entity.getTmpObjectKey())
        .finalObjectKey(entity.getFinalObjectKey())
        .imgOrder(entity.getImgOrder())
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .build();
  }
}
