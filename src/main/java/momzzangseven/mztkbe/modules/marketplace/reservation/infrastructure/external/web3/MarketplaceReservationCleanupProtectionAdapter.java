package momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.external.web3;

import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowAction;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.persistence.repository.ReservationCreateIdempotencyJpaRepository;
import momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.persistence.repository.ReservationJpaRepository;
import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceExecutionCleanupIntent;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.CheckMarketplaceReservationCleanupProtectionPort;
import momzzangseven.mztkbe.modules.web3.marketplace.domain.vo.MarketplaceExecutionActionType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "web3",
    name = {"eip7702.enabled", "eip7702.cleanup.enabled"},
    havingValue = "true")
public class MarketplaceReservationCleanupProtectionAdapter
    implements CheckMarketplaceReservationCleanupProtectionPort {

  private final ReservationJpaRepository reservationJpaRepository;
  private final ReservationCreateIdempotencyJpaRepository createIdempotencyJpaRepository;

  @Override
  public List<String> findProtectedExecutionIntentPublicIds(
      List<MarketplaceExecutionCleanupIntent> intents) {
    if (intents == null || intents.isEmpty()) {
      return List.of();
    }
    Set<String> protectedPublicIds = new LinkedHashSet<>();
    List<String> publicIds =
        intents.stream()
            .map(MarketplaceExecutionCleanupIntent::publicId)
            .filter(id -> id != null && !id.isBlank())
            .toList();
    if (!publicIds.isEmpty()) {
      protectedPublicIds.addAll(
          reservationJpaRepository.findCurrentExecutionIntentPublicIdsIn(publicIds));
      protectedPublicIds.addAll(
          createIdempotencyJpaRepository.findCurrentExecutionIntentPublicIdsIn(publicIds));
    }

    for (MarketplaceExecutionCleanupIntent intent : intents) {
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

  private boolean hasUnboundPendingAction(MarketplaceExecutionCleanupIntent intent) {
    return parseLong(intent.resourceId())
        .map(
            reservationId ->
                reservationJpaRepository.countUnboundPendingAction(
                        reservationId,
                        pendingActions(intent.actionType()),
                        pendingStatuses(intent.actionType()))
                    > 0)
        .orElse(false);
  }

  private Set<ReservationEscrowAction> pendingActions(MarketplaceExecutionActionType actionType) {
    return switch (actionType) {
      case MARKETPLACE_CLASS_PURCHASE -> EnumSet.of(ReservationEscrowAction.PURCHASE);
      case MARKETPLACE_CLASS_CANCEL ->
          EnumSet.of(ReservationEscrowAction.BUYER_CANCEL, ReservationEscrowAction.TRAINER_REJECT);
      case MARKETPLACE_CLASS_CONFIRM -> EnumSet.of(ReservationEscrowAction.BUYER_CONFIRM);
      case MARKETPLACE_CLASS_EXPIRED_REFUND -> EnumSet.of(ReservationEscrowAction.DEADLINE_REFUND);
    };
  }

  private Set<ReservationStatus> pendingStatuses(MarketplaceExecutionActionType actionType) {
    return switch (actionType) {
      case MARKETPLACE_CLASS_PURCHASE ->
          EnumSet.of(ReservationStatus.PURCHASE_PREPARING, ReservationStatus.PURCHASE_PENDING);
      case MARKETPLACE_CLASS_CANCEL ->
          EnumSet.of(ReservationStatus.CANCEL_PENDING, ReservationStatus.REJECT_PENDING);
      case MARKETPLACE_CLASS_CONFIRM -> EnumSet.of(ReservationStatus.CONFIRM_PENDING);
      case MARKETPLACE_CLASS_EXPIRED_REFUND ->
          EnumSet.of(ReservationStatus.DEADLINE_REFUND_PENDING);
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
