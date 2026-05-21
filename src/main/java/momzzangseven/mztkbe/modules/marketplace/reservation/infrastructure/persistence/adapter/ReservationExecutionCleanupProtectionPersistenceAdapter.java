package momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.persistence.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationExecutionCleanupProtectionQuery;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationExecutionCleanupProtectionPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationActionStateStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowAction;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.persistence.repository.MarketplaceReservationActionStateJpaRepository;
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
  private static final String ADMIN_REFUND = "MARKETPLACE_ADMIN_REFUND";
  private static final String ADMIN_SETTLE = "MARKETPLACE_ADMIN_SETTLE";
  private static final List<String> ACTIVE_ACTION_STATE_STATUSES =
      EnumSet.of(ReservationActionStateStatus.PREPARING, ReservationActionStateStatus.INTENT_BOUND)
          .stream()
          .map(Enum::name)
          .toList();

  private final ReservationJpaRepository reservationJpaRepository;
  private final MarketplaceReservationActionStateJpaRepository actionStateJpaRepository;
  private final ObjectMapper objectMapper = new ObjectMapper();

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
          actionStateJpaRepository.findExecutionIntentPublicIdsInByStatusIn(
              publicIds,
              EnumSet.of(
                      ReservationActionStateStatus.PREPARING,
                      ReservationActionStateStatus.INTENT_BOUND)
                  .stream()
                  .map(Enum::name)
                  .toList()));
    }

    for (ReservationExecutionCleanupProtectionQuery intent : intents) {
      if (intent.publicId() == null
          || intent.publicId().isBlank()
          || protectedPublicIds.contains(intent.publicId())) {
        continue;
      }
      EvidenceResult evidenceResult = readEvidence(intent);
      if (!evidenceResult.valid()) {
        protectedPublicIds.add(intent.publicId());
        continue;
      }
      if (hasActiveEvidence(intent, evidenceResult.evidence())) {
        protectedPublicIds.add(intent.publicId());
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
                reservationJpaRepository.countUnboundPendingAction(
                        reservationId, toNames(actions), toNames(statuses))
                    > 0)
        .orElse(false);
  }

  private boolean hasActiveEvidence(
      ReservationExecutionCleanupProtectionQuery intent, MarketplacePayloadEvidence evidence) {
    if (evidence == null) {
      return false;
    }
    Set<ReservationEscrowAction> actions = pendingActions(intent.actionType());
    if (actions.isEmpty()) {
      return false;
    }
    return actionStateJpaRepository.countActiveByPayloadEvidence(
            evidence.actionStateId(),
            evidence.reservationId(),
            evidence.escrowId(),
            actions.stream().map(Enum::name).toList(),
            evidence.pendingAttemptToken(),
            ACTIVE_ACTION_STATE_STATUSES)
        > 0;
  }

  private EvidenceResult readEvidence(ReservationExecutionCleanupProtectionQuery intent) {
    if (!isMarketplaceAction(intent.actionType())) {
      return EvidenceResult.valid(null);
    }
    if (intent.payloadSnapshotJson() == null || intent.payloadSnapshotJson().isBlank()) {
      return EvidenceResult.invalid();
    }
    try {
      JsonNode root = objectMapper.readTree(intent.payloadSnapshotJson());
      int payloadVersion = root.path("payloadVersion").asInt(0);
      if (payloadVersion != 1) {
        return EvidenceResult.invalid();
      }
      String payloadActionType = text(root, "actionType");
      Long reservationId = positiveLong(root, "reservationId");
      Long escrowId = positiveLong(root, "escrowId");
      Long actionStateId = positiveLong(root, "actionStateId");
      String pendingAttemptToken = text(root, "pendingAttemptToken");
      if (pendingAttemptToken == null || pendingAttemptToken.isBlank()) {
        pendingAttemptToken = text(root, "attemptToken");
      }
      if (!intent.actionType().equals(payloadActionType)
          || reservationId == null
          || escrowId == null
          || actionStateId == null
          || pendingAttemptToken == null
          || pendingAttemptToken.isBlank()) {
        return EvidenceResult.invalid();
      }
      return EvidenceResult.valid(
          new MarketplacePayloadEvidence(
              reservationId, escrowId, actionStateId, pendingAttemptToken));
    } catch (Exception ex) {
      return EvidenceResult.invalid();
    }
  }

  private boolean isMarketplaceAction(String actionType) {
    return PURCHASE.equals(actionType)
        || CANCEL.equals(actionType)
        || CONFIRM.equals(actionType)
        || EXPIRED_REFUND.equals(actionType)
        || ADMIN_REFUND.equals(actionType)
        || ADMIN_SETTLE.equals(actionType);
  }

  private String text(JsonNode root, String fieldName) {
    JsonNode node = root.get(fieldName);
    return node == null || node.isNull() ? null : node.asText();
  }

  private Long positiveLong(JsonNode root, String fieldName) {
    JsonNode node = root.get(fieldName);
    if (node == null || node.isNull() || !node.canConvertToLong()) {
      return null;
    }
    long value = node.asLong();
    return value > 0 ? value : null;
  }

  private Set<ReservationEscrowAction> pendingActions(String actionType) {
    return switch (actionType) {
      case PURCHASE -> EnumSet.of(ReservationEscrowAction.PURCHASE);
      case CANCEL ->
          EnumSet.of(ReservationEscrowAction.BUYER_CANCEL, ReservationEscrowAction.TRAINER_REJECT);
      case CONFIRM -> EnumSet.of(ReservationEscrowAction.BUYER_CONFIRM);
      case EXPIRED_REFUND -> EnumSet.of(ReservationEscrowAction.DEADLINE_REFUND);
      case ADMIN_REFUND -> EnumSet.of(ReservationEscrowAction.ADMIN_REFUND);
      case ADMIN_SETTLE -> EnumSet.of(ReservationEscrowAction.ADMIN_SETTLE);
      default -> EnumSet.noneOf(ReservationEscrowAction.class);
    };
  }

  private Set<ReservationStatus> pendingStatuses(String actionType) {
    return switch (actionType) {
      case PURCHASE ->
          EnumSet.of(
              ReservationStatus.HOLDING,
              ReservationStatus.PURCHASE_PREPARING,
              ReservationStatus.PURCHASE_PENDING);
      case CANCEL -> EnumSet.of(ReservationStatus.CANCEL_PENDING, ReservationStatus.REJECT_PENDING);
      case CONFIRM -> EnumSet.of(ReservationStatus.CONFIRM_PENDING);
      case EXPIRED_REFUND -> EnumSet.of(ReservationStatus.DEADLINE_REFUND_PENDING);
      case ADMIN_REFUND -> EnumSet.of(ReservationStatus.ADMIN_REFUND_PENDING);
      case ADMIN_SETTLE -> EnumSet.of(ReservationStatus.ADMIN_SETTLE_PENDING);
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

  private static <E extends Enum<E>> Set<String> toNames(Set<E> values) {
    return values.stream().map(Enum::name).collect(java.util.stream.Collectors.toSet());
  }

  private record EvidenceResult(boolean valid, MarketplacePayloadEvidence evidence) {
    private static EvidenceResult valid(MarketplacePayloadEvidence evidence) {
      return new EvidenceResult(true, evidence);
    }

    private static EvidenceResult invalid() {
      return new EvidenceResult(false, null);
    }
  }

  private record MarketplacePayloadEvidence(
      Long reservationId, Long escrowId, Long actionStateId, String pendingAttemptToken) {}
}
