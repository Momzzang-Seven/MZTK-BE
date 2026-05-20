package momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.persistence.adapter;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.BindReservationActionStatePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationActionStatePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SaveReservationActionStatePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.MarketplaceReservationActionState;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationActionStateStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowAction;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowActorType;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.persistence.entity.MarketplaceReservationActionStateEntity;
import momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.persistence.repository.MarketplaceReservationActionStateJpaRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class MarketplaceReservationActionStatePersistenceAdapter
    implements LoadReservationActionStatePort,
        SaveReservationActionStatePort,
        BindReservationActionStatePort {

  private final MarketplaceReservationActionStateJpaRepository repository;
  private final Clock clock;

  @Override
  public Optional<MarketplaceReservationActionState> findById(Long actionStateId) {
    return repository.findById(actionStateId).map(this::toDomain);
  }

  @Override
  public Optional<MarketplaceReservationActionState> findByIdWithLock(Long actionStateId) {
    return repository.findByIdWithLock(actionStateId).map(this::toDomain);
  }

  @Override
  public Optional<MarketplaceReservationActionState> findLatestByReservationId(Long reservationId) {
    return repository.findLatestByReservationId(reservationId, PageRequest.of(0, 1)).stream()
        .findFirst()
        .map(this::toDomain);
  }

  @Override
  public Optional<MarketplaceReservationActionState> findLatestByReservationIdWithLock(
      Long reservationId) {
    return repository
        .findLatestByReservationIdWithLock(reservationId, PageRequest.of(0, 1))
        .stream()
        .findFirst()
        .map(this::toDomain);
  }

  @Override
  public Optional<MarketplaceReservationActionState> findByExecutionIntentPublicIdWithLock(
      String executionIntentPublicId) {
    return repository
        .findByExecutionIntentPublicIdWithLock(executionIntentPublicId)
        .map(this::toDomain);
  }

  @Override
  public List<MarketplaceReservationActionState> findByReservationIdAndStatuses(
      Long reservationId, List<ReservationActionStateStatus> statuses) {
    return repository
        .findByReservationIdAndStatusIn(reservationId, statuses.stream().map(Enum::name).toList())
        .stream()
        .map(this::toDomain)
        .toList();
  }

  @Override
  public Optional<MarketplaceReservationActionState> findLatestByReservationIdAndActionType(
      Long reservationId, ReservationEscrowAction actionType) {
    return repository
        .findLatestByReservationIdAndActionType(
            reservationId, actionType.name(), PageRequest.of(0, 1))
        .stream()
        .findFirst()
        .map(this::toDomain);
  }

  @Override
  public Optional<MarketplaceReservationActionState> findLatestByReservationIdAndActionTypeWithLock(
      Long reservationId, ReservationEscrowAction actionType) {
    return repository
        .findLatestByReservationIdAndActionTypeWithLock(
            reservationId, actionType.name(), PageRequest.of(0, 1))
        .stream()
        .findFirst()
        .map(this::toDomain);
  }

  @Override
  public MarketplaceReservationActionState save(MarketplaceReservationActionState actionState) {
    return toDomain(repository.save(toEntity(actionState)));
  }

  @Override
  @Transactional
  public void markStaleForRetry(Long actionStateId, String errorReason) {
    repository.markStaleForRetry(actionStateId, errorReason, LocalDateTime.now(clock));
  }

  @Override
  @Transactional
  public Optional<MarketplaceReservationActionState> bindExecutionIntent(
      Long actionStateId, String attemptToken, String executionIntentPublicId) {
    int updated =
        repository.bindExecutionIntent(
            actionStateId, attemptToken, executionIntentPublicId, LocalDateTime.now(clock));
    if (updated == 0) {
      return Optional.empty();
    }
    return repository
        .findByExecutionIntentPublicIdWithLock(executionIntentPublicId)
        .map(this::toDomain);
  }

  private MarketplaceReservationActionState toDomain(
      MarketplaceReservationActionStateEntity entity) {
    return MarketplaceReservationActionState.builder()
        .id(entity.getId())
        .reservationId(entity.getReservationId())
        .escrowId(entity.getEscrowId())
        .actionType(ReservationEscrowAction.valueOf(entity.getActionType()))
        .actorType(ReservationEscrowActorType.valueOf(entity.getActorType()))
        .actorUserId(entity.getActorUserId())
        .attemptNo(entity.getAttemptNo())
        .attemptToken(entity.getAttemptToken())
        .executionIntentPublicId(entity.getExecutionIntentPublicId())
        .rootIdempotencyKey(entity.getRootIdempotencyKey())
        .payloadHash(entity.getPayloadHash())
        .status(ReservationActionStateStatus.valueOf(entity.getStatus()))
        .expectedReservationVersion(entity.getExpectedReservationVersion())
        .expectedReservationStatus(
            toEnum(entity.getExpectedReservationStatus(), ReservationStatus.class))
        .expectedEscrowStatus(
            toEnum(entity.getExpectedEscrowStatus(), ReservationEscrowStatus.class))
        .priorReservationStatus(toEnum(entity.getPriorReservationStatus(), ReservationStatus.class))
        .priorEscrowStatus(toEnum(entity.getPriorEscrowStatus(), ReservationEscrowStatus.class))
        .preparationExpiresAt(entity.getPreparationExpiresAt())
        .serverSignatureSignedAt(entity.getServerSignatureSignedAt())
        .serverSignatureExpiresAt(entity.getServerSignatureExpiresAt())
        .actionReason(entity.getActionReason())
        .retryable(entity.getRetryable())
        .errorCode(entity.getErrorCode())
        .errorReason(entity.getErrorReason())
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .build();
  }

  private MarketplaceReservationActionStateEntity toEntity(
      MarketplaceReservationActionState domain) {
    return MarketplaceReservationActionStateEntity.builder()
        .id(domain.getId())
        .reservationId(domain.getReservationId())
        .escrowId(domain.getEscrowId())
        .actionType(domain.getActionType().name())
        .actorType(domain.getActorType().name())
        .actorUserId(domain.getActorUserId())
        .attemptNo(domain.getAttemptNo())
        .attemptToken(domain.getAttemptToken())
        .executionIntentPublicId(domain.getExecutionIntentPublicId())
        .rootIdempotencyKey(domain.getRootIdempotencyKey())
        .payloadHash(domain.getPayloadHash())
        .status(domain.getStatus().name())
        .expectedReservationVersion(domain.getExpectedReservationVersion())
        .expectedReservationStatus(toName(domain.getExpectedReservationStatus()))
        .expectedEscrowStatus(toName(domain.getExpectedEscrowStatus()))
        .priorReservationStatus(toName(domain.getPriorReservationStatus()))
        .priorEscrowStatus(toName(domain.getPriorEscrowStatus()))
        .preparationExpiresAt(domain.getPreparationExpiresAt())
        .serverSignatureSignedAt(domain.getServerSignatureSignedAt())
        .serverSignatureExpiresAt(domain.getServerSignatureExpiresAt())
        .actionReason(domain.getActionReason())
        .retryable(domain.getRetryable())
        .errorCode(domain.getErrorCode())
        .errorReason(domain.getErrorReason())
        .createdAt(domain.getCreatedAt())
        .updatedAt(domain.getUpdatedAt())
        .build();
  }

  private static <E extends Enum<E>> E toEnum(String value, Class<E> enumType) {
    return value == null ? null : Enum.valueOf(enumType, value);
  }

  private static String toName(Enum<?> value) {
    return value == null ? null : value.name();
  }
}
