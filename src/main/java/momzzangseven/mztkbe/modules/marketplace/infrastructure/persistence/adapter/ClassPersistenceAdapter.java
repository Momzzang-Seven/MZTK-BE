package momzzangseven.mztkbe.modules.marketplace.infrastructure.persistence.adapter;

import static momzzangseven.mztkbe.modules.marketplace.infrastructure.persistence.entity.QMarketplaceClassEntity.marketplaceClassEntity;
import static momzzangseven.mztkbe.modules.marketplace.infrastructure.persistence.entity.QTrainerStoreEntity.trainerStoreEntity;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.marketplace.application.MarketplacePaginationConstants;
import momzzangseven.mztkbe.modules.marketplace.application.dto.ClassDetailInfo;
import momzzangseven.mztkbe.modules.marketplace.application.dto.ClassItem;
import momzzangseven.mztkbe.modules.marketplace.application.port.out.LoadClassPort;
import momzzangseven.mztkbe.modules.marketplace.application.port.out.LoadClassTagPort;
import momzzangseven.mztkbe.modules.marketplace.application.port.out.SaveClassPort;
import momzzangseven.mztkbe.modules.marketplace.domain.model.MarketplaceClass;
import momzzangseven.mztkbe.modules.marketplace.domain.vo.ClassCategory;
import momzzangseven.mztkbe.modules.marketplace.infrastructure.persistence.entity.MarketplaceClassEntity;
import momzzangseven.mztkbe.modules.marketplace.infrastructure.persistence.entity.QClassSlotEntity;
import momzzangseven.mztkbe.modules.marketplace.infrastructure.persistence.entity.TrainerStoreEntity;
import momzzangseven.mztkbe.modules.marketplace.infrastructure.persistence.repository.MarketplaceClassJpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

/**
 * Persistence adapter implementing {@link LoadClassPort} and {@link SaveClassPort}.
 *
 * <h2>findActiveClasses query strategy</h2>
 *
 * <ul>
 *   <li><b>Filter</b>: {@code active = true}, optional {@code category} / {@code trainerId}.
 *       Time-range filter ({@code startTime} / {@code endTime}) uses a correlated EXISTS sub-query
 *       against {@code class_slots} so only classes with at least one matching slot are returned.
 *   <li><b>Distance sort</b>: PostGIS {@code ST_Distance(location::geography,
 *       ST_MakePoint(lng,lat)::geography)} injected via {@code Expressions.numberTemplate} (metres
 *       → km). When lat/lng are absent but DISTANCE sort was requested, falls back to RATING.
 *   <li><b>RATING sort</b>: placeholder ORDER BY {@code id DESC} — replace with an actual rating
 *       column once the review module is available.
 *   <li><b>Count query</b>: separated from the content fetch for pagination correctness and
 *       performance.
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClassPersistenceAdapter implements LoadClassPort, SaveClassPort {

  private static final String SORT_DISTANCE = "DISTANCE";
  private static final String SORT_RATING = "RATING";
  private static final String SORT_PRICE_ASC = "PRICE_ASC";
  private static final String SORT_PRICE_DESC = "PRICE_DESC";

  /** Metres per kilometre — for converting PostGIS ST_Distance result to km. */
  private static final double METRES_PER_KM = 1000.0;

  private final MarketplaceClassJpaRepository classJpaRepository;
  private final LoadClassTagPort loadClassTagPort;
  private final JPAQueryFactory queryFactory;

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
   * <p>Full QueryDSL implementation: applies all filter predicates and sort specifiers. The
   * time-range filter uses a correlated EXISTS sub-query so the {@code class_slot_days} join table
   * is never pulled into the outer query.
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
        "Finding active classes: category={}, sort={}, trainerId={}, startTime={}, endTime={}",
        category,
        sort,
        trainerId,
        startTime,
        endTime);

    // Resolve effective sort first — determines whether a distance JOIN is needed
    String effectiveSort = resolveEffectiveSort(sort, lat, lng);
    boolean needsDistanceJoin = SORT_DISTANCE.equals(effectiveSort);

    // Distance expression — null when location data is absent
    NumberExpression<Double> distanceKmExpr = buildDistanceExpression(lat, lng);

    BooleanBuilder where = buildWhereClause(category, trainerId, startTime, endTime);

    List<OrderSpecifier<?>> orders = buildOrderSpecifiers(effectiveSort, distanceKmExpr);

    // ── Content fetch ──────────────────────────────────────────────────────
    // When sorting by DISTANCE the ORDER BY clause references trainerStoreEntity.location;
    // we must LEFT JOIN trainer_stores so QueryDSL/Hibernate can resolve that path.
    var contentQuery = queryFactory.selectFrom(marketplaceClassEntity).where(where);

    if (needsDistanceJoin) {
      contentQuery =
          contentQuery
              .leftJoin(trainerStoreEntity)
              .on(trainerStoreEntity.trainerId.eq(marketplaceClassEntity.trainerId));
    }

    List<MarketplaceClassEntity> entities =
        contentQuery
            .orderBy(orders.toArray(new OrderSpecifier[0]))
            .offset(pageable.getOffset())
            .limit(pageable.getPageSize())
            .fetch();

    // ── Count query (separated for performance) ────────────────────────────
    Long total =
        queryFactory
            .select(marketplaceClassEntity.count())
            .from(marketplaceClassEntity)
            .where(where)
            .fetchOne();
    long totalCount = total != null ? total : 0L;

    List<ClassItem> items = toClassItems(entities, distanceKmExpr);

    return new PageImpl<>(items, pageable, totalCount);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Uses a single JPQL LEFT JOIN to load the class and its trainer's store in one round-trip,
   * eliminating the N+1 query pattern.
   */
  @Override
  public Optional<ClassDetailInfo> findClassDetailById(Long classId) {
    log.debug("Loading class detail by id: {}", classId);

    List<Object[]> rows = classJpaRepository.findClassWithStore(classId);
    if (rows.isEmpty()) {
      return Optional.empty();
    }

    Object[] row = rows.get(0);
    MarketplaceClassEntity entity = (MarketplaceClassEntity) row[0];
    TrainerStoreEntity store = (TrainerStoreEntity) row[1]; // null when trainer has no store

    List<String> tags = loadClassTagPort.findTagNamesByClassId(classId);
    // Decode features via toDomainWithTags (avoids duplicating the decode() logic)
    List<String> features = entity.toDomainWithTags(List.of()).getFeatures();

    return Optional.of(
        new ClassDetailInfo(
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
            // classTimes loaded separately by GetClassDetailService via LoadClassSlotPort
            List.of()));
  }

  @Override
  public Page<MarketplaceClass> findByTrainerId(Long trainerId, Pageable pageable) {
    log.debug("Loading classes for trainerId={}", trainerId);

    Page<MarketplaceClassEntity> entityPage =
        classJpaRepository.findByTrainerIdOrderByCreatedAtDesc(trainerId, pageable);

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
    return saved.toDomainWithTags(marketplaceClass.getTags());
  }

  // ============================================
  // Private helpers — query building
  // ============================================

  /**
   * Builds a PostGIS distance expression (in km) for ordering by proximity.
   *
   * <p>SQL generated: {@code ST_Distance(ts.location::geography, ST_MakePoint(lng,lat)::geography)
   * / 1000}
   *
   * @return distance expression, or {@code null} when lat/lng are not provided
   */
  private NumberExpression<Double> buildDistanceExpression(Double lat, Double lng) {
    if (lat == null || lng == null) {
      return null;
    }
    // trainer_stores.location is geometry(Point,4326); cast to geography for metre output
    return Expressions.numberTemplate(
            Double.class,
            "ST_Distance("
                + "({0}).location ::geography, "
                + "ST_MakePoint({1}, {2}) ::geography"
                + ")",
            trainerStoreEntity,
            lng,
            lat)
        .divide(METRES_PER_KM);
  }

  /**
   * Assembles the WHERE predicate.
   *
   * <ul>
   *   <li>Always: {@code active = true}
   *   <li>Optional: {@code category}, {@code trainerId}, time-range EXISTS sub-query
   * </ul>
   */
  private BooleanBuilder buildWhereClause(
      String category, Long trainerId, String startTime, String endTime) {

    BooleanBuilder where = new BooleanBuilder();
    where.and(marketplaceClassEntity.active.isTrue());

    if (category != null && !category.isBlank()) {
      try {
        ClassCategory cat = ClassCategory.valueOf(category.toUpperCase());
        where.and(marketplaceClassEntity.category.eq(cat));
      } catch (IllegalArgumentException ignored) {
        log.warn("Unknown category filter value ignored: {}", category);
      }
    }

    if (trainerId != null) {
      where.and(marketplaceClassEntity.trainerId.eq(trainerId));
    }

    if (startTime != null || endTime != null) {
      where.and(buildTimeRangeSubQuery(startTime, endTime));
    }

    return where;
  }

  /**
   * Correlated EXISTS sub-query that filters classes to those having at least one active slot whose
   * {@code startTime} falls within the requested window.
   *
   * <p>Both bounds are optional (open-ended range when omitted).
   */
  private com.querydsl.core.types.Predicate buildTimeRangeSubQuery(
      String startTime, String endTime) {

    QClassSlotEntity slot = QClassSlotEntity.classSlotEntity;

    BooleanBuilder timePredicate = new BooleanBuilder();
    timePredicate.and(slot.classId.eq(marketplaceClassEntity.id));
    timePredicate.and(slot.active.isTrue());

    if (startTime != null && !startTime.isBlank()) {
      timePredicate.and(slot.startTime.goe(LocalTime.parse(startTime)));
    }
    if (endTime != null && !endTime.isBlank()) {
      timePredicate.and(slot.startTime.lt(LocalTime.parse(endTime)));
    }

    return JPAExpressions.selectOne().from(slot).where(timePredicate).exists();
  }

  /**
   * Builds ORDER BY specifiers from a pre-resolved sort string.
   *
   * <p>The caller must invoke {@link #resolveEffectiveSort} before calling this method so that
   * fallback logic (DISTANCE without lat/lng → RATING) is applied exactly once.
   *
   * <table>
   *   <tr><th>effectiveSort value</th><th>ORDER BY</th></tr>
   *   <tr><td>DISTANCE</td><td>ST_Distance ASC, created_at DESC</td></tr>
   *   <tr><td>RATING</td><td>id DESC (placeholder until review module exists)</td></tr>
   *   <tr><td>LATEST</td><td>created_at DESC</td></tr>
   *   <tr><td>PRICE_ASC</td><td>price_amount ASC</td></tr>
   *   <tr><td>PRICE_DESC</td><td>price_amount DESC</td></tr>
   *   <tr><td>unknown/null</td><td>LATEST fallback (unreachable in normal flow; resolveEffectiveSort
   *       normalises to {@link MarketplacePaginationConstants#DEFAULT_SORT} first)</td></tr>
   * </table>
   *
   * @param effectiveSort already-resolved sort key (never null/blank)
   * @param distanceKmExpr PostGIS distance expression; non-null only when lat/lng present
   */
  private List<OrderSpecifier<?>> buildOrderSpecifiers(
      String effectiveSort, NumberExpression<Double> distanceKmExpr) {

    List<OrderSpecifier<?>> specifiers = new ArrayList<>();

    switch (effectiveSort) {
      case SORT_DISTANCE -> {
        specifiers.add(distanceKmExpr.asc());
        specifiers.add(marketplaceClassEntity.createdAt.desc());
      }
      case SORT_PRICE_ASC -> specifiers.add(marketplaceClassEntity.priceAmount.asc());
      case SORT_PRICE_DESC -> specifiers.add(marketplaceClassEntity.priceAmount.desc());
      case SORT_RATING ->
          // TODO: replace with rating column once the review module is available
          specifiers.add(marketplaceClassEntity.id.desc());
      default -> specifiers.add(marketplaceClassEntity.createdAt.desc()); // LATEST
    }

    return specifiers;
  }

  /**
   * Resolves the final ORDER BY sort key for the QueryDSL query.
   *
   * <p>This method is intentionally a defensive duplicate of the service-layer fallback in {@link
   * momzzangseven.mztkbe.modules.marketplace.application.service.GetClassesService#resolveSort}.
   * When called from {@code GetClassesService}, the service already converts {@code null} sorts to
   * RATING/DISTANCE and DISTANCE-without-location to RATING, so the {@code null/blank} branch here
   * is unreachable in the current call path.
   *
   * <p>The duplication is intentional: it keeps the adapter self-contained so that future callers
   * (e.g., admin APIs) that bypass the service do not silently receive an unexpected sort.
   *
   * <ul>
   *   <li>DISTANCE without lat/lng → {@link MarketplacePaginationConstants#DEFAULT_SORT}
   *   <li>null/blank → {@link MarketplacePaginationConstants#DEFAULT_SORT}
   *   <li>anything else → uppercased and passed to {@link #buildOrderSpecifiers}
   * </ul>
   */
  private String resolveEffectiveSort(String sort, Double lat, Double lng) {
    if (SORT_DISTANCE.equalsIgnoreCase(sort) && (lat == null || lng == null)) {
      return MarketplacePaginationConstants.DEFAULT_SORT;
    }
    if (sort == null || sort.isBlank()) {
      return MarketplacePaginationConstants.DEFAULT_SORT;
    }
    return sort.toUpperCase();
  }

  /**
   * Converts entity list to {@link ClassItem} projections.
   *
   * <p>When location data is available, re-runs a BatchJoin query against {@code trainer_stores} to
   * fetch the distance for every class in the page (one query for the whole page, not N queries).
   * Thumbnail keys and tags are populated upstream by {@link
   * momzzangseven.mztkbe.modules.marketplace.application.service.GetClassesService}.
   */
  private List<ClassItem> toClassItems(
      List<MarketplaceClassEntity> entities, NumberExpression<Double> distanceKmExpr) {

    Map<Long, Double> distanceMap = Map.of();

    if (distanceKmExpr != null && !entities.isEmpty()) {
      List<Long> entityIds = entities.stream().map(MarketplaceClassEntity::getId).toList();
      distanceMap =
          queryFactory
              .select(marketplaceClassEntity.id, distanceKmExpr)
              .from(marketplaceClassEntity)
              .leftJoin(trainerStoreEntity)
              .on(trainerStoreEntity.trainerId.eq(marketplaceClassEntity.trainerId))
              .where(marketplaceClassEntity.id.in(entityIds))
              .fetch()
              .stream()
              .filter(t -> t.get(0, Long.class) != null)
              .collect(
                  Collectors.toMap(
                      t -> t.get(0, Long.class),
                      t -> t.get(1, Double.class), // may be null when store has no location
                      (a, b) -> a));
    }

    final Map<Long, Double> dm = distanceMap;
    return entities.stream()
        .map(
            entity ->
                new ClassItem(
                    entity.getId(),
                    entity.getTitle(),
                    entity.getCategory(),
                    entity.getPriceAmount(),
                    entity.getDurationMinutes(),
                    null, // thumbnail: populated later by GetClassesService
                    List.of(), // tags: populated later by GetClassesService
                    dm.get(entity.getId())))
        .toList();
  }
}
