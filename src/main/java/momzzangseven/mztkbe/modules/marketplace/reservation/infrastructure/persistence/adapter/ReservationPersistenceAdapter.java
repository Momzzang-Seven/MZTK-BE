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
  public int countActiveReservationsBySlotId(Long slotId) {
    return reservationJpaRepository.countActiveBySlotId(slotId);
  }

  @Override
  public int countActiveReservationsBySlotIdWithLock(Long slotId) {
    return reservationJpaRepository.countActiveBySlotIdWithLock(slotId);
  }

  @Override
  public Map<Long, Integer> countActiveReservationsBySlotIdIn(List<Long> slotIds) {
    if (slotIds == null || slotIds.isEmpty()) return Map.of();
    List<Object[]> rows = reservationJpaRepository.countActiveBySlotIdIn(slotIds);
    Map<Long, Integer> result = new HashMap<>();
    for (Object[] row : rows) {
      result.put((Long) row[0], ((Long) row[1]).intValue());
    }
    return result;
  }

  @Override
  public List<Reservation> findPendingForAutoCancel(
      LocalDateTime nowMinusTimeout, LocalDateTime nowPlusWindow, int batchSize) {
    return reservationJpaRepository
        .findPendingForAutoCancel(nowMinusTimeout, nowPlusWindow)
        .stream()
        .limit(batchSize)
        .map(ReservationEntity::toDomain)
        .toList();
  }

  @Override
  public List<Reservation> findApprovedForAutoSettle(LocalDateTime now, int batchSize) {
    return reservationJpaRepository.findApprovedForAutoSettle(now).stream()
        .limit(batchSize)
        .map(ReservationEntity::toDomain)
        .toList();
  }

  @Override
  public boolean hasAnyReservationHistory(Long slotId) {
    return reservationJpaRepository.existsBySlotId(slotId);
  }

  @Override
  public Reservation save(Reservation reservation) {
    return reservationJpaRepository.save(ReservationEntity.fromDomain(reservation)).toDomain();
  }
}
