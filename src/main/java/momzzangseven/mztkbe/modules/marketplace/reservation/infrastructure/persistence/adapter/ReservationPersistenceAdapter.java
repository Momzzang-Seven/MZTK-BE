package momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.persistence.adapter;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.pagination.CursorPageRequest;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SaveReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.Reservation;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.persistence.entity.ReservationEntity;
import momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.persistence.repository.ReservationJpaRepository;
import momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.persistence.repository.ReservationSlotDateLockJpaRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationPersistenceAdapter implements LoadReservationPort, SaveReservationPort {

  private final ReservationJpaRepository reservationJpaRepository;
  private final ReservationSlotDateLockJpaRepository slotDateLockJpaRepository;
  private final JdbcTemplate jdbcTemplate;
  private final Clock clock;

  @Override
  public Optional<Reservation> findById(Long reservationId) {
    return reservationJpaRepository.findById(reservationId).map(ReservationEntity::toDomain);
  }

  @Override
  public Optional<Reservation> findByIdWithLock(Long reservationId) {
    return reservationJpaRepository
        .findByIdWithLock(reservationId)
        .map(ReservationEntity::toDomain);
  }

  @Override
  public Optional<Reservation> findByCurrentExecutionIntentPublicIdWithLock(String publicId) {
    return reservationJpaRepository
        .findByCurrentExecutionIntentPublicIdWithLock(publicId)
        .map(ReservationEntity::toDomain);
  }

  @Override
  public Optional<Reservation> findActiveByBuyerAndSlotDateTimeWithLock(
      Long buyerId, Long slotId, LocalDate reservationDate, LocalTime reservationTime) {
    return reservationJpaRepository
        .findActiveByBuyerAndSlotDateTimeWithLock(
            buyerId, slotId, reservationDate, reservationTime, PageRequest.of(0, 1))
        .stream()
        .findFirst()
        .map(ReservationEntity::toDomain);
  }

  @Override
  public int countActiveReservationsBySlotId(Long slotId) {
    return reservationJpaRepository.countActiveBySlotId(slotId, activeCountNow());
  }

  @Override
  public Map<Long, Integer> countActiveReservationsBySlotIds(List<Long> slotIds) {
    if (slotIds == null || slotIds.isEmpty()) return new HashMap<>();
    List<Object[]> results =
        reservationJpaRepository.countActiveBySlotIdIn(slotIds, activeCountNow());
    Map<Long, Integer> map = new HashMap<>();
    for (Object[] row : results) {
      map.put((Long) row[0], ((Long) row[1]).intValue());
    }
    return map;
  }

  @Override
  public int countActiveReservationsBySlotIdAndDate(Long slotId, java.time.LocalDate date) {
    return reservationJpaRepository.countActiveBySlotIdAndDate(slotId, date, activeCountNow());
  }

  @Override
  public int countActiveReservationsBySlotIdAndDateWithLock(Long slotId, java.time.LocalDate date) {
    return reservationJpaRepository.countActiveBySlotIdAndDateWithLock(
        slotId, date, activeCountNow());
  }

  @Override
  public void lockSlotDateCapacityKey(Long slotId, java.time.LocalDate date) {
    insertSlotDateLockIfAbsent(slotId, date);
    slotDateLockJpaRepository
        .findBySlotIdAndReservationDateForUpdate(slotId, date)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Failed to lock slot/date capacity key: slotId=" + slotId + ", date=" + date));
  }

  private void insertSlotDateLockIfAbsent(Long slotId, java.time.LocalDate date) {
    String databaseProductName =
        jdbcTemplate.execute(
            (ConnectionCallback<String>)
                connection -> connection.getMetaData().getDatabaseProductName());
    if (databaseProductName != null && databaseProductName.toLowerCase().contains("h2")) {
      jdbcTemplate.update(
          """
          MERGE INTO reservation_slot_date_locks (class_slot_id, reservation_date, created_at)
          KEY (class_slot_id, reservation_date)
          VALUES (?, ?, CURRENT_TIMESTAMP)
          """,
          slotId,
          date);
      return;
    }

    jdbcTemplate.update(
        """
        INSERT INTO reservation_slot_date_locks (class_slot_id, reservation_date, created_at)
        VALUES (?, ?, CURRENT_TIMESTAMP)
        ON CONFLICT (class_slot_id, reservation_date) DO NOTHING
        """,
        slotId,
        date);
  }

  @Override
  public Map<java.time.LocalDate, Integer> countActiveReservationsBySlotIdAndDateRange(
      Long slotId, java.time.LocalDate startDate, java.time.LocalDate endDate) {
    List<Object[]> rows =
        reservationJpaRepository.countActiveBySlotIdAndDateRange(
            slotId, startDate, endDate, activeCountNow());
    Map<java.time.LocalDate, Integer> result = new HashMap<>();
    for (Object[] row : rows) {
      // JPA might return Date or LocalDate, assuming it returns LocalDate or java.sql.Date
      Object dateObj = row[0];
      java.time.LocalDate date =
          (dateObj instanceof java.sql.Date)
              ? ((java.sql.Date) dateObj).toLocalDate()
              : (java.time.LocalDate) dateObj;
      result.put(date, ((Long) row[1]).intValue());
    }
    return result;
  }

  private LocalDateTime activeCountNow() {
    return LocalDateTime.now(clock);
  }

  @Override
  public List<Reservation> findPendingForAutoCancel(
      LocalDateTime nowMinusTimeout, LocalDateTime nowPlusWindow, int batchSize) {
    return reservationJpaRepository
        .findPendingForAutoCancel(
            nowPlusWindow.minusHours(1),
            nowMinusTimeout,
            nowPlusWindow.toLocalDate(),
            nowPlusWindow.toLocalTime(),
            org.springframework.data.domain.PageRequest.of(0, batchSize))
        .stream()
        .map(ReservationEntity::toDomain)
        .toList();
  }

  @Override
  public List<Reservation> findApprovedForAutoSettle(LocalDateTime now, int batchSize) {
    // Session end + 24h < now -> Session end < now - 24h
    LocalDateTime target = now.minusHours(24);
    java.time.LocalDate targetDate = target.toLocalDate();

    // Fetch batchSize * 2 to account for some rows that might be filtered out
    return reservationJpaRepository
        .findApprovedCandidates(
            now, targetDate, org.springframework.data.domain.PageRequest.of(0, batchSize * 2))
        .stream()
        .map(ReservationEntity::toDomain)
        .filter(r -> r.sessionEndAt().plusHours(24).isBefore(now))
        .limit(batchSize)
        .toList();
  }

  @Override
  public boolean hasAnyReservationHistory(Long slotId) {
    return reservationJpaRepository.existsBySlotId(slotId);
  }

  @Override
  public List<Reservation> findByUserId(Long userId, ReservationStatus status) {
    return reservationJpaRepository.findByUserId(userId, status).stream()
        .map(ReservationEntity::toDomain)
        .toList();
  }

  @Override
  public List<Reservation> findByUserIdCursor(
      Long userId, ReservationStatus status, CursorPageRequest pageRequest) {
    // KeysetCursor.createdAt encodes reservationDate.atTime(reservationTime) — NOT the entity's
    // created_at column. Both date and time are extracted here for the 3-tuple keyset predicate.
    LocalDate cursorDate =
        pageRequest.hasCursor() ? pageRequest.cursor().createdAt().toLocalDate() : null;
    LocalTime cursorTime =
        pageRequest.hasCursor() ? pageRequest.cursor().createdAt().toLocalTime() : null;
    Long cursorId = pageRequest.hasCursor() ? pageRequest.cursor().id() : null;
    PageRequest page = PageRequest.of(0, pageRequest.limitWithProbe());

    // Route to the status-less query when no status filter is specified so that the
    // (user_id, reservation_date DESC, reservation_time DESC, id DESC) index can be used without
    // interference from the status column in the status-aware index.
    List<ReservationEntity> rows =
        status == null
            ? reservationJpaRepository.findByUserIdCursorNoStatus(
                userId, cursorDate, cursorTime, cursorId, page)
            : reservationJpaRepository.findByUserIdCursor(
                userId, status, cursorDate, cursorTime, cursorId, page);
    return rows.stream().map(ReservationEntity::toDomain).toList();
  }

  @Override
  public List<Reservation> findByTrainerId(Long trainerId, ReservationStatus status) {
    return reservationJpaRepository.findByTrainerId(trainerId, status).stream()
        .map(ReservationEntity::toDomain)
        .toList();
  }

  @Override
  public List<Reservation> findByTrainerIdCursor(
      Long trainerId, ReservationStatus status, CursorPageRequest pageRequest) {
    // KeysetCursor.createdAt encodes reservationDate.atTime(reservationTime) — see
    // findByUserIdCursor.
    LocalDate cursorDate =
        pageRequest.hasCursor() ? pageRequest.cursor().createdAt().toLocalDate() : null;
    LocalTime cursorTime =
        pageRequest.hasCursor() ? pageRequest.cursor().createdAt().toLocalTime() : null;
    Long cursorId = pageRequest.hasCursor() ? pageRequest.cursor().id() : null;
    PageRequest page = PageRequest.of(0, pageRequest.limitWithProbe());

    List<ReservationEntity> rows =
        status == null
            ? reservationJpaRepository.findByTrainerIdCursorNoStatus(
                trainerId, cursorDate, cursorTime, cursorId, page)
            : reservationJpaRepository.findByTrainerIdCursor(
                trainerId, status, cursorDate, cursorTime, cursorId, page);
    return rows.stream().map(ReservationEntity::toDomain).toList();
  }

  @Override
  public Reservation save(Reservation reservation) {
    return reservationJpaRepository.save(ReservationEntity.fromDomain(reservation)).toDomain();
  }
}
