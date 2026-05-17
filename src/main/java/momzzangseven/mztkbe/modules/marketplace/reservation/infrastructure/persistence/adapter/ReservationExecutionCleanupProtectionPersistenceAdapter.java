package momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.persistence.adapter;

import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationExecutionCleanupProtectionQuery;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationExecutionCleanupProtectionPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationCreateIdempotencyStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowAction;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.persistence.repository.ReservationCreateIdempotencyJpaRepository;
import momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.persistence.repository.ReservationJpaRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReservationExecutionCleanupProtectionPersistenceAdapter
    implements LoadReservationExecutionCleanupProtectionPort {

  private static final String PURCHASE = "MARKETPLACE_CLASS_PURCHASE";
  private static final String CANCEL = "MARKETPLACE_CLASS_CANCEL";
  private static final String CONFIRM = "MARKETPLACE_CLASS_CONFIRM";
  private static final String EXPIRED_REFUND = "MARKETPLACE_CLASS_EXPIRED_REFUND";

  private final ReservationJpaRepository reservationJpaRepository;
  private final ReservationCreateIdempotencyJpaRepository createIdempotencyJpaRepository;

  @Override
  public List<String> findProtectedExecutionIntentPublicIds(
      List<ReservationExecutionCleanupProtectionQuery> intents) {
    if (intents == null || intents.isEmpty()) {
      return List.of();
    }
    Set<String> protectedPublicIds = new LinkedHashSet<>();
    List<String> publicIds =
        intents.stream()
            .map(ReservationExecutionCleanupProtectionQuery::publicId)
            .filter(id -> id != null && !id.isBlank())
            .toList();
    if (!publicIds.isEmpty()) {
      protectedPublicIds.addAll(
          reservationJpaRepository.findCurrentExecutionIntentPublicIdsIn(publicIds));
      protectedPublicIds.addAll(
          createIdempotencyJpaRepository.findCurrentExecutionIntentPublicIdsIn(
              publicIds,
              EnumSet.of(
                  ReservationCreateIdempotencyStatus.INTENT_CREATED,
                  ReservationCreateIdempotencyStatus.BOUND)));
    }

    for (ReservationExecutionCleanupProtectionQuery intent : intents) {
      if (intent.publicId() == null
          || intent.publicId().isBlank()
          || protectedPublicIds.contains(intent.publicId())) {
        continue;
      }
      if (hasUnboundPendingAction(intent)) {
        protectedPublicIds.add(intent.publicId());
      }
    }
    return List.copyOf(protectedPublicIds);
  }

  private boolean hasUnboundPendingAction(ReservationExecutionCleanupProtectionQuery intent) {
    Set<ReservationEscrowAction> actions = pendingActions(intent.actionType());
    Set<ReservationStatus> statuses = pendingStatuses(intent.actionType());
    if (actions.isEmpty() || statuses.isEmpty()) {
      return false;
    }
    return parseLong(intent.resourceId())
        .map(
            reservationId ->
                reservationJpaRepository.countUnboundPendingAction(reservationId, actions, statuses)
                    > 0)
        .orElse(false);
  }

  private Set<ReservationEscrowAction> pendingActions(String actionType) {
    return switch (actionType) {
      case PURCHASE -> EnumSet.of(ReservationEscrowAction.PURCHASE);
      case CANCEL ->
          EnumSet.of(ReservationEscrowAction.BUYER_CANCEL, ReservationEscrowAction.TRAINER_REJECT);
      case CONFIRM -> EnumSet.of(ReservationEscrowAction.BUYER_CONFIRM);
      case EXPIRED_REFUND -> EnumSet.of(ReservationEscrowAction.DEADLINE_REFUND);
      default -> EnumSet.noneOf(ReservationEscrowAction.class);
    };
  }

  private Set<ReservationStatus> pendingStatuses(String actionType) {
    return switch (actionType) {
      case PURCHASE ->
          EnumSet.of(ReservationStatus.PURCHASE_PREPARING, ReservationStatus.PURCHASE_PENDING);
      case CANCEL -> EnumSet.of(ReservationStatus.CANCEL_PENDING, ReservationStatus.REJECT_PENDING);
      case CONFIRM -> EnumSet.of(ReservationStatus.CONFIRM_PENDING);
      case EXPIRED_REFUND -> EnumSet.of(ReservationStatus.DEADLINE_REFUND_PENDING);
      default -> EnumSet.noneOf(ReservationStatus.class);
    };
  }

  private Optional<Long> parseLong(String value) {
    try {
      return value == null || value.isBlank() ? Optional.empty() : Optional.of(Long.valueOf(value));
    } catch (NumberFormatException ignored) {
      return Optional.empty();
    }
  }
}
