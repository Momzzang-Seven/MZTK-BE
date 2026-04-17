package momzzangseven.mztkbe.modules.marketplace.infrastructure.persistence.adapter;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.marketplace.application.dto.ClassDetailInfo;
import momzzangseven.mztkbe.modules.marketplace.application.dto.ClassItem;
import momzzangseven.mztkbe.modules.marketplace.application.port.out.LoadClassPort;
import momzzangseven.mztkbe.modules.marketplace.application.port.out.LoadClassTagPort;
import momzzangseven.mztkbe.modules.marketplace.application.port.out.SaveClassPort;
import momzzangseven.mztkbe.modules.marketplace.domain.model.MarketplaceClass;
import momzzangseven.mztkbe.modules.marketplace.infrastructure.persistence.entity.MarketplaceClassEntity;
import momzzangseven.mztkbe.modules.marketplace.infrastructure.persistence.entity.TrainerStoreEntity;
import momzzangseven.mztkbe.modules.marketplace.infrastructure.persistence.repository.MarketplaceClassJpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

/**
 * Persistence adapter implementing {@link LoadClassPort} and {@link SaveClassPort}.
 *
 * <p>Tags are loaded via {@link LoadClassTagPort} and injected into the domain model via {@link
 * MarketplaceClassEntity#toDomainWithTags}. This delegates tag management to the global tag system,
 * identical to the post module pattern.
 *
 * <p>For dynamic queries (filtering by location, category, sort, time) a QueryDSL implementation
 * stub is provided. Replace {@link #findActiveClasses} with a proper Q-entity predicate.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClassPersistenceAdapter implements LoadClassPort, SaveClassPort {

  private final MarketplaceClassJpaRepository classJpaRepository;
  private final LoadClassTagPort loadClassTagPort;

  // ========== LoadClassPort ==========

  @Override
  public Optional<MarketplaceClass> findById(Long classId) {
    log.debug("Loading class by id: {}", classId);
    return classJpaRepository
        .findById(classId)
        .map(
            entity -> {
              List<String> tags = loadClassTagPort.findTagNamesByClassId(classId);
              return entity.toDomainWithTags(tags);
            });
  }

  /**
   * {@inheritDoc}
   *
   * <p>Uses {@code findByActiveTrueOrderByCreatedAtDesc} for correct, DB-level active filtering.
   * Category, sort, and location filters are stubbed pending QueryDSL implementation.
   */
  @Override
  public Page<ClassItem> findActiveClasses(
      Double lat,
      Double lng,
      String category,
      String sort,
      Long trainerId,
      String startTime,
      String endTime,
      Pageable pageable) {

    log.debug(
        "Finding active classes: category={}, sort={}, trainerId={}", category, sort, trainerId);

    Page<MarketplaceClassEntity> entityPage =
        classJpaRepository.findByActiveTrueOrderByCreatedAtDesc(pageable);

    // Batch-load tags to avoid N+1 queries
    List<Long> classIds =
        entityPage.getContent().stream().map(MarketplaceClassEntity::getId).toList();
    Map<Long, List<String>> tagMap =
        classIds.isEmpty() ? Map.of() : loadClassTagPort.findTagsByClassIdsIn(classIds);

    List<ClassItem> items =
        entityPage.getContent().stream()
            .map(entity -> toClassItem(entity, tagMap.getOrDefault(entity.getId(), List.of())))
            .toList();

    return new PageImpl<>(items, pageable, entityPage.getTotalElements());
  }

  /**
   * {@inheritDoc}
   *
   * <p>Uses a single JPQL LEFT JOIN to load the class and its trainer's store in one round-trip,
   * eliminating the N+1 query pattern that would arise from a separate store lookup.
   */
  @Override
  public Optional<ClassDetailInfo> findClassDetailById(Long classId) {
    log.debug("Loading class detail by id: {}", classId);

    return classJpaRepository
        .findClassWithStore(classId)
        .map(
            row -> {
              MarketplaceClassEntity entity = (MarketplaceClassEntity) row[0];
              TrainerStoreEntity store = (TrainerStoreEntity) row[1]; // null when no store

              List<String> tags = loadClassTagPort.findTagNamesByClassId(classId);
              List<String> features = entity.toDomain().getFeatures();

              return new ClassDetailInfo(
                  entity.getId(),
                  entity.getTrainerId(),
                  store != null ? store.getId() : null,
                  store != null ? store.getStoreName() : null,
                  store != null ? store.getAddress() : null,
                  store != null ? store.getDetailAddress() : null,
                  store != null ? store.getLatitude() : null,
                  store != null ? store.getLongitude() : null,
                  entity.getTitle(),
                  entity.getCategory() != null ? entity.getCategory().name() : null,
                  entity.getDescription(),
                  entity.getPriceAmount(),
                  entity.getDurationMinutes(),
                  tags,
                  features,
                  entity.getPersonalItems(),
                  // classTimes are loaded by GetClassDetailService via LoadClassSlotPort
                  List.of());
            });
  }

  @Override
  public Page<MarketplaceClass> findByTrainerId(Long trainerId, Pageable pageable) {
    log.debug("Loading classes for trainerId={}", trainerId);

    Page<MarketplaceClassEntity> entityPage =
        classJpaRepository.findByTrainerIdOrderByCreatedAtDesc(trainerId, pageable);

    // Batch-load tags to avoid N+1 queries
    List<Long> ids = entityPage.getContent().stream().map(MarketplaceClassEntity::getId).toList();
    Map<Long, List<String>> tagMap =
        ids.isEmpty() ? Map.of() : loadClassTagPort.findTagsByClassIdsIn(ids);

    List<MarketplaceClass> domains =
        entityPage.getContent().stream()
            .map(entity -> entity.toDomainWithTags(tagMap.getOrDefault(entity.getId(), List.of())))
            .toList();

    return new PageImpl<>(domains, pageable, entityPage.getTotalElements());
  }

  // ========== SaveClassPort ==========

  @Override
  public MarketplaceClass save(MarketplaceClass marketplaceClass) {
    log.debug("Saving class for trainerId={}", marketplaceClass.getTrainerId());
    MarketplaceClassEntity entity = MarketplaceClassEntity.fromDomain(marketplaceClass);
    MarketplaceClassEntity saved = classJpaRepository.save(entity);
    log.debug("Class saved with id={}", saved.getId());
    // Return domain with the original tags the caller passed (tags are managed separately)
    return saved.toDomainWithTags(marketplaceClass.getTags());
  }

  // ============================================
  // Private helpers
  // ============================================

  private ClassItem toClassItem(MarketplaceClassEntity entity, List<String> tags) {
    return new ClassItem(
        entity.getId(),
        entity.getTitle(),
        entity.getCategory(),
        entity.getPriceAmount(),
        entity.getDurationMinutes(),
        null, // thumbnail: populated later by image module
        tags,
        null // distance: populated later if lat/lng provided
        );
  }
}
