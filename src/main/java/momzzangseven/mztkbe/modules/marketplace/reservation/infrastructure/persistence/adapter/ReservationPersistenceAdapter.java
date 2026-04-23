package momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.persistence.adapter;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SaveReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.Reservation;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.persistence.entity.ReservationEntity;
import momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.persistence.repository.ReservationJpaRepository;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationPersistenceAdapter implements LoadReservationPort, SaveReservationPort {

  private final ReservationJpaRepository reservationJpaRepository;

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
  public int countActiveReservationsBySlotId(Long slotId) {
    return reservationJpaRepository.countActiveBySlotId(slotId);
  }

  @Override
  public Map<Long, Integer> countActiveReservationsBySlotIds(List<Long> slotIds) {
    if (slotIds == null || slotIds.isEmpty()) return new HashMap<>();
    List<Object[]> results = reservationJpaRepository.countActiveBySlotIdIn(slotIds);
    Map<Long, Integer> map = new HashMap<>();
    for (Object[] row : results) {
      map.put((Long) row[0], ((Long) row[1]).intValue());
    }
    return map;
  }

  @Override
  public int countActiveReservationsBySlotIdAndDate(Long slotId, java.time.LocalDate date) {
    return reservationJpaRepository.countActiveBySlotIdAndDate(slotId, date);
  }

  @Override
  public int countActiveReservationsBySlotIdAndDateWithLock(Long slotId, java.time.LocalDate date) {
    return reservationJpaRepository.countActiveBySlotIdAndDateWithLock(slotId, date);
  }

  @Override
  public Map<java.time.LocalDate, Integer> countActiveReservationsBySlotIdAndDateRange(
      Long slotId, java.time.LocalDate startDate, java.time.LocalDate endDate) {
    List<Object[]> rows =
        reservationJpaRepository.countActiveBySlotIdAndDateRange(slotId, startDate, endDate);
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

  @Override
  public List<Reservation> findPendingForAutoCancel(
      LocalDateTime nowMinusTimeout, LocalDateTime nowPlusWindow, int batchSize) {
    return reservationJpaRepository
        .findPendingForAutoCancel(
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
            targetDate, org.springframework.data.domain.PageRequest.of(0, batchSize * 2))
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
  public List<Reservation> findByTrainerId(Long trainerId, ReservationStatus status) {
    return reservationJpaRepository.findByTrainerId(trainerId, status).stream()
        .map(ReservationEntity::toDomain)
        .toList();
  }

  @Override
  public Reservation save(Reservation reservation) {
    return reservationJpaRepository.save(ReservationEntity.fromDomain(reservation)).toDomain();
  }
}
