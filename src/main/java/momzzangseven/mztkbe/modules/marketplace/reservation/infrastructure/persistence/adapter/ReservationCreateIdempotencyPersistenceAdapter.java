package momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.persistence.adapter;

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
  public ReservationCreateIdempotency save(ReservationCreateIdempotency idempotency) {
    return toDomain(repository.save(ReservationCreateIdempotencyEntity.fromDomain(idempotency)));
  }

  private ReservationCreateIdempotency toDomain(ReservationCreateIdempotencyEntity entity) {
    return entity.toDomain();
  }
}
