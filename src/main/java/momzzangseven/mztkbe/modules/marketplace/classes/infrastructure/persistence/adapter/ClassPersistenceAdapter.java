package momzzangseven.mztkbe.modules.marketplace.classes.infrastructure.persistence.adapter;

import static momzzangseven.mztkbe.modules.marketplace.classes.infrastructure.persistence.entity.QMarketplaceClassEntity.marketplaceClassEntity;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.marketplace.classes.application.MarketplacePaginationConstants;
import momzzangseven.mztkbe.modules.marketplace.classes.application.dto.ClassDetailInfo;
import momzzangseven.mztkbe.modules.marketplace.classes.application.dto.ClassItem;
import momzzangseven.mztkbe.modules.marketplace.classes.application.port.out.LoadClassPort;
import momzzangseven.mztkbe.modules.marketplace.classes.application.port.out.LoadClassTagPort;
import momzzangseven.mztkbe.modules.marketplace.classes.application.port.out.SaveClassPort;
import momzzangseven.mztkbe.modules.marketplace.classes.domain.model.MarketplaceClass;
import momzzangseven.mztkbe.modules.marketplace.classes.domain.vo.ClassCategory;
import momzzangseven.mztkbe.modules.marketplace.classes.infrastructure.persistence.entity.MarketplaceClassEntity;
import momzzangseven.mztkbe.modules.marketplace.classes.infrastructure.persistence.entity.QClassSlotEntity;
import momzzangseven.mztkbe.modules.marketplace.classes.infrastructure.persistence.repository.MarketplaceClassJpaRepository;
import momzzangseven.mztkbe.modules.marketplace.store.infrastructure.persistence.entity.TrainerStoreEntity;
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
 *   <li><b>Distance sort</b>: Delegates to {@link #findActiveClassesByDistance} which uses a native
 *       PostGIS SQL query with {@code ST_Distance(CAST(location AS geography),
 *       CAST(ST_MakePoint(lng,lat) AS geography))} because JPQL cannot parse the PostgreSQL {@code
 *       ::} cast operator. When lat/lng are absent but DISTANCE sort was requested, falls back to
 *       RATING.
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
  private final EntityManager entityManager;

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
   * <p>When sort is {@code DISTANCE}, delegates to a native PostGIS SQL query because JPQL does not
   * support the PostgreSQL {@code ::} cast operator required by PostGIS geography functions. All
   * other sort modes use QueryDSL JPQL.
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

    String effectiveSort = resolveEffectiveSort(sort, lat, lng);

    // DISTANCE sort uses PostGIS geography functions — must use native SQL because
    // JPQL cannot parse the PostgreSQL :: cast operator (e.g. ::geography).
    if (SORT_DISTANCE.equals(effectiveSort)) {
      return findActiveClassesByDistance(
          lat, lng, category, trainerId, startTime, endTime, pageable);
    }

    BooleanBuilder where = buildWhereClause(category, trainerId, startTime, endTime);
    List<OrderSpecifier<?>> orders = buildOrderSpecifiers(effectiveSort);

    List<MarketplaceClassEntity> entities =
        queryFactory
            .selectFrom(marketplaceClassEntity)
            .where(where)
            .orderBy(orders.toArray(new OrderSpecifier[0]))
            .offset(pageable.getOffset())
            .limit(pageable.getPageSize())
            .fetch();

    Long total =
        queryFactory
            .select(marketplaceClassEntity.count())
            .from(marketplaceClassEntity)
            .where(where)
            .fetchOne();
    long totalCount = total != null ? total : 0L;

    List<ClassItem> items = toClassItems(entities);
    return new PageImpl<>(items, pageable, totalCount);
  }

  /**
   * Native SQL implementation for DISTANCE sort.
   *
   * <p>Uses PostGIS {@code ST_Distance(CAST(location AS geography), CAST(ST_MakePoint(lng,lat) AS
   * geography))} for metre-accurate distance ordering. The equivalent {@code ::geography}
   * PostgreSQL cast cannot be expressed in JPQL because Hibernate's native-query parser confuses
   * {@code ::} with named-parameter prefixes.
   *
   * <p>Filters mirror those of the JPQL path: {@code active = true}, optional category, trainerId,
   * and time-range (EXISTS on class_slots).
   *
   * <p><b>Warning</b>: if new filters are added to {@link #buildWhereClause}, they must also be
   * mirrored here to maintain consistent behaviour across sort modes.
   */
  @SuppressWarnings("unchecked")
  private Page<ClassItem> findActiveClassesByDistance(
      double lat,
      double lng,
      String category,
      Long trainerId,
      String startTime,
      String endTime,
      Pageable pageable) {

    // ── Build dynamic WHERE fragments ─────────────────────────────────────
    StringBuilder where = new StringBuilder("mc.active = true");
    if (category != null && !category.isBlank()) {
      where.append(" AND mc.category = :category");
    }
    if (trainerId != null) {
      where.append(" AND mc.trainer_id = :trainerId");
    }
    if ((startTime != null && !startTime.isBlank()) || (endTime != null && !endTime.isBlank())) {
      where.append(
          " AND EXISTS ("
              + "SELECT 1 FROM class_slots cs "
              + "WHERE cs.class_id = mc.id AND cs.active = true");
      if (startTime != null && !startTime.isBlank()) {
        where.append(" AND cs.start_time >= :startTime");
      }
      if (endTime != null && !endTime.isBlank()) {
        where.append(" AND cs.start_time < :endTime");
      }
      where.append(")");
    }

    // lat/lng are double primitives — safe to embed as SQL literals (no injection risk).
    // This completely avoids Hibernate's named-parameter parser, which treats every ':'
    // as a parameter prefix and fails to recognise ':lat'/':lng' when the SQL string
    // also contains CAST() expressions or is repeated in ORDER BY.
    String distanceExpr =
        "ST_Distance("
            + "CAST(ts.location AS geography), "
            + "CAST(ST_MakePoint("
            + lng
            + ", "
            + lat
            + ") AS geography)"
            + ") / "
            + METRES_PER_KM;

    String contentSql =
        "SELECT mc.id, mc.title, mc.category, mc.price_amount, mc.duration_minutes, "
            + distanceExpr
            + " AS distance_km "
            + "FROM marketplace_classes mc "
            + "LEFT JOIN trainer_stores ts ON ts.user_id = mc.trainer_id "
            + "WHERE "
            + where
            + " ORDER BY "
            + distanceExpr
            + " ASC, mc.created_at DESC"
            + " LIMIT "
            + pageable.getPageSize()
            + " OFFSET "
            + pageable.getOffset();

    String countSql =
        "SELECT COUNT(*) FROM marketplace_classes mc "
            + "LEFT JOIN trainer_stores ts ON ts.user_id = mc.trainer_id "
            + "WHERE "
            + where;

    Query contentQuery = entityManager.createNativeQuery(contentSql);
    Query countQuery = entityManager.createNativeQuery(countSql);

    // Bind only the string/id parameters that cannot be inlined safely
    for (Query q : List.of(contentQuery, countQuery)) {
      if (category != null && !category.isBlank()) {
        q.setParameter("category", category.toUpperCase());
      }
      if (trainerId != null) {
        q.setParameter("trainerId", trainerId);
      }
      if (startTime != null && !startTime.isBlank()) {
        q.setParameter("startTime", startTime);
      }
      if (endTime != null && !endTime.isBlank()) {
        q.setParameter("endTime", endTime);
      }
    }

    @SuppressWarnings("rawtypes")
    List rawRows = contentQuery.getResultList();
    // Native queries with multiple SELECTed columns always return List<Object[]>.
    // Wrap in a helper to guard against single-column or single-row edge cases.
    List<Object[]> rows =
        rawRows.stream()
            .map(
                r -> {
                  if (r instanceof Object[] arr) return arr;
                  // Single-column fallback (should not happen with 6 columns, but defensive)
                  return new Object[] {r};
                })
            .toList();
    Number countResult = (Number) countQuery.getSingleResult();
    long total = countResult.longValue();

    List<ClassItem> items =
        rows.stream()
            .map(
                row -> {
                  Long id = ((Number) row[0]).longValue();
                  String title = (String) row[1];
                  String categoryStr = (String) row[2];
                  ClassCategory cat =
                      categoryStr != null ? ClassCategory.valueOf(categoryStr) : null;
                  Integer priceAmount = row[3] != null ? ((Number) row[3]).intValue() : null;
                  Integer durationMinutes = row[4] != null ? ((Number) row[4]).intValue() : null;
                  Double distanceKm = row[5] != null ? ((Number) row[5]).doubleValue() : null;
                  return new ClassItem(
                      id, title, cat, priceAmount, durationMinutes, null, List.of(), distanceKm);
                })
            .toList();

    return new PageImpl<>(items, pageable, total);
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

  // buildDistanceExpression removed: DISTANCE sort now uses native SQL via
  // findActiveClassesByDistance() — JPQL cannot parse the PostgreSQL ::geography cast operator.

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

    if ((startTime != null && !startTime.isBlank()) || (endTime != null && !endTime.isBlank())) {
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
   * <p>DISTANCE sort is handled by the native SQL path ({@link #findActiveClassesByDistance}); this
   * method is never called with {@code SORT_DISTANCE}.
   *
   * <table>
   *   <tr><th>effectiveSort value</th><th>ORDER BY</th></tr>
   *   <tr><td>RATING</td><td>id DESC (placeholder until review module exists)</td></tr>
   *   <tr><td>LATEST</td><td>created_at DESC</td></tr>
   *   <tr><td>PRICE_ASC</td><td>price_amount ASC</td></tr>
   *   <tr><td>PRICE_DESC</td><td>price_amount DESC</td></tr>
   *   <tr><td>unknown/null</td><td>LATEST fallback</td></tr>
   * </table>
   *
   * @param effectiveSort already-resolved sort key (never DISTANCE/null/blank in this path)
   */
  private List<OrderSpecifier<?>> buildOrderSpecifiers(String effectiveSort) {

    List<OrderSpecifier<?>> specifiers = new ArrayList<>();

    switch (effectiveSort) {
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
   * Converts entity list to {@link ClassItem} projections (non-DISTANCE sort path).
   *
   * <p>Distance is always {@code null} here; when DISTANCE sort is active the native SQL path
   * ({@link #findActiveClassesByDistance}) builds {@link ClassItem} objects directly with the
   * computed distance. Thumbnail keys and tags are populated upstream by {@link
   * momzzangseven.mztkbe.modules.marketplace.application.service.GetClassesService}.
   */
  private List<ClassItem> toClassItems(List<MarketplaceClassEntity> entities) {
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
                    null)) // distance: populated by native SQL path only
        .toList();
  }
}
