package momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.persistence.adapter;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Optional;
import javax.sql.DataSource;
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
  private final DataSource dataSource;

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
    return toDomain(repository.save(toEntity(idempotency)));
  }

  @Override
  public ReserveCreateIdempotencyResult reservePreparing(
      Long buyerId, String keyHash, String payloadHash, LocalDateTime expiresAt) {
    if (isH2Database()) {
      return reservePreparingWithJpaFallback(buyerId, keyHash, payloadHash, expiresAt);
    }
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

  private ReserveCreateIdempotencyResult reservePreparingWithJpaFallback(
      Long buyerId, String keyHash, String payloadHash, LocalDateTime expiresAt) {
    Optional<ReservationCreateIdempotency> existing =
        repository.findByBuyerIdAndKeyHashWithLock(buyerId, keyHash).map(this::toDomain);
    if (existing.isPresent()) {
      return new ReserveCreateIdempotencyResult(existing.get(), false);
    }
    ReservationCreateIdempotency created =
        toDomain(
            repository.save(
                toEntity(
                    ReservationCreateIdempotency.preparing(
                        buyerId, keyHash, payloadHash, expiresAt))));
    return new ReserveCreateIdempotencyResult(created, true);
  }

  private boolean isH2Database() {
    try (var connection = dataSource.getConnection()) {
      return "H2".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName());
    } catch (SQLException e) {
      throw new IllegalStateException("failed to inspect reservation idempotency database", e);
    }
  }

  private ReservationCreateIdempotency toDomain(ReservationCreateIdempotencyEntity entity) {
    return ReservationCreateIdempotency.builder()
        .id(entity.getId())
        .buyerId(entity.getBuyerId())
        .keyHash(entity.getKeyHash())
        .payloadHash(entity.getPayloadHash())
        .status(entity.getStatus())
        .reservationId(entity.getReservationId())
        .currentExecutionIntentPublicId(entity.getCurrentExecutionIntentPublicId())
        .responseSnapshotJson(entity.getResponseSnapshotJson())
        .expiresAt(entity.getExpiresAt())
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .build();
  }

  private ReservationCreateIdempotencyEntity toEntity(ReservationCreateIdempotency domain) {
    return ReservationCreateIdempotencyEntity.builder()
        .id(domain.getId())
        .buyerId(domain.getBuyerId())
        .keyHash(domain.getKeyHash())
        .payloadHash(domain.getPayloadHash())
        .status(domain.getStatus())
        .reservationId(domain.getReservationId())
        .currentExecutionIntentPublicId(domain.getCurrentExecutionIntentPublicId())
        .responseSnapshotJson(domain.getResponseSnapshotJson())
        .expiresAt(domain.getExpiresAt())
        .createdAt(domain.getCreatedAt())
        .updatedAt(domain.getUpdatedAt())
        .build();
  }
}
