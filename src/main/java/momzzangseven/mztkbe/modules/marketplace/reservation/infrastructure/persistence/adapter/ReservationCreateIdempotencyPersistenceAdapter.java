package momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.persistence.adapter;

import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationCreateIdempotencyPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SaveReservationCreateIdempotencyPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.ReservationCreateIdempotency;
import momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.persistence.entity.ReservationCreateIdempotencyEntity;
import momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.persistence.repository.ReservationCreateIdempotencyJpaRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReservationCreateIdempotencyPersistenceAdapter
    implements LoadReservationCreateIdempotencyPort, SaveReservationCreateIdempotencyPort {

  private final ReservationCreateIdempotencyJpaRepository repository;

  @Override
  public Optional<ReservationCreateIdempotency> findByBuyerIdAndKeyHashWithLock(
      Long buyerId, String keyHash) {
    return repository.findByBuyerIdAndKeyHashWithLock(buyerId, keyHash).map(this::toDomain);
  }

  @Override
  public Optional<ReservationCreateIdempotency> findByReservationIdWithLock(Long reservationId) {
    return repository.findByReservationIdWithLock(reservationId).map(this::toDomain);
  }

  @Override
  public ReservationCreateIdempotency save(ReservationCreateIdempotency idempotency) {
    return toDomain(repository.save(ReservationCreateIdempotencyEntity.fromDomain(idempotency)));
  }

  @Override
  public ReserveCreateIdempotencyResult reservePreparing(
      Long buyerId, String keyHash, String payloadHash, LocalDateTime expiresAt) {
    int inserted = repository.insertPreparingIfAbsent(buyerId, keyHash, payloadHash, expiresAt);
    ReservationCreateIdempotency idempotency =
        repository
            .findByBuyerIdAndKeyHashWithLock(buyerId, keyHash)
            .map(this::toDomain)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "reservation create idempotency row was not created or found"));
    return new ReserveCreateIdempotencyResult(idempotency, inserted > 0);
  }

  private ReservationCreateIdempotency toDomain(ReservationCreateIdempotencyEntity entity) {
    return entity.toDomain();
  }
}
