package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationExecutionCandidateView;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationExecutionStateView;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationExecutionCandidatePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationExecutionStatePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.MarketplaceReservationActionState;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.Reservation;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowAction;

/** Shared guard for detecting unresolved shared execution intents for a reservation action. */
@RequiredArgsConstructor
final class ReservationExecutionCandidateGuard {

  private static final String STATUS_AWAITING_SIGNATURE = "AWAITING_SIGNATURE";
  private static final String STATUS_SIGNED = "SIGNED";
  private static final String STATUS_PENDING_ONCHAIN = "PENDING_ONCHAIN";
  private static final String STATUS_CONFIRMED = "CONFIRMED";
  private static final String TRANSACTION_SUCCEEDED = "SUCCEEDED";
  private static final String TRANSACTION_UNCONFIRMED = "UNCONFIRMED";
  private static final Set<String> MARKETPLACE_ACTION_CODES =
      Set.of(
          "MARKETPLACE_CLASS_PURCHASE",
          "MARKETPLACE_CLASS_CANCEL",
          "MARKETPLACE_CLASS_CONFIRM",
          "MARKETPLACE_CLASS_EXPIRED_REFUND",
          "MARKETPLACE_ADMIN_REFUND",
          "MARKETPLACE_ADMIN_SETTLE");

  private final LoadReservationExecutionStatePort loadReservationExecutionStatePort;
  private final LoadReservationExecutionCandidatePort loadReservationExecutionCandidatePort;

  boolean hasBlockingExecution(
      Reservation reservation, MarketplaceReservationActionState actionState) {
    if (reservation == null || actionState == null) {
      return false;
    }
    String intentId = actionState.getExecutionIntentPublicId();
    if (intentId != null
        && !intentId.isBlank()
        && isBlockingExecutionState(loadReservationExecutionStatePort.loadState(intentId))) {
      return true;
    }
    return executionCandidates(reservation)
        .filter(candidate -> matchesAction(actionState.getActionType(), candidate.actionType()))
        .filter(candidate -> isSameAttempt(reservation, actionState, candidate))
        .anyMatch(this::isBlockingExecutionCandidate);
  }

  Optional<String> findAwaitingSignatureIntent(
      Reservation reservation, MarketplaceReservationActionState actionState) {
    if (reservation == null || actionState == null) {
      return Optional.empty();
    }
    String intentId = actionState.getExecutionIntentPublicId();
    if (intentId != null
        && !intentId.isBlank()
        && isAwaitingSignature(loadReservationExecutionStatePort.loadState(intentId))) {
      return Optional.of(intentId);
    }
    return executionCandidates(reservation)
        .filter(candidate -> matchesAction(actionState.getActionType(), candidate.actionType()))
        .filter(candidate -> isSameAttempt(reservation, actionState, candidate))
        .filter(candidate -> isAwaitingSignature(candidate.status()))
        .map(ReservationExecutionCandidateView::executionIntentId)
        .findFirst();
  }

  boolean hasBlockingExecutionForAnyMarketplaceAction(Reservation reservation) {
    if (reservation == null) {
      return false;
    }
    return executionCandidates(reservation)
        .filter(candidate -> MARKETPLACE_ACTION_CODES.contains(candidate.actionType()))
        .filter(candidate -> matchesReservationEvidence(reservation, candidate))
        .anyMatch(this::isBlockingExecutionCandidate);
  }

  boolean hasBlockingExecutionForAction(
      Reservation reservation, ReservationEscrowAction actionType) {
    if (reservation == null || actionType == null) {
      return false;
    }
    return executionCandidates(reservation)
        .filter(candidate -> matchesAction(actionType, candidate.actionType()))
        .filter(candidate -> matchesReservationEvidence(reservation, candidate))
        .anyMatch(this::isBlockingExecutionCandidate);
  }

  private Stream<ReservationExecutionCandidateView> executionCandidates(Reservation reservation) {
    List<ReservationExecutionCandidateView> candidates =
        loadReservationExecutionCandidatePort.findByReservationResource(
            reservation.getId(), reservation.getOrderKey());
    return candidates == null ? Stream.empty() : candidates.stream();
  }

  private boolean isSameAttempt(
      Reservation reservation,
      MarketplaceReservationActionState actionState,
      ReservationExecutionCandidateView candidate) {
    if (!candidate.payloadEvidenceValid() || candidate.payloadEvidence() == null) {
      return true;
    }
    ReservationExecutionCandidateView.PayloadEvidence evidence = candidate.payloadEvidence();
    return Objects.equals(reservation.getId(), evidence.reservationId())
        && Objects.equals(actionState.getId(), evidence.actionStateId())
        && Objects.equals(actionState.getEscrowId(), evidence.escrowId())
        && Objects.equals(actionState.getAttemptToken(), evidence.pendingAttemptToken())
        && Objects.equals(reservation.getOrderKey(), evidence.orderKey())
        && matchesAction(actionState.getActionType(), evidence.actionType());
  }

  private boolean matchesReservationEvidence(
      Reservation reservation, ReservationExecutionCandidateView candidate) {
    if (!candidate.payloadEvidenceValid() || candidate.payloadEvidence() == null) {
      return true;
    }
    ReservationExecutionCandidateView.PayloadEvidence evidence = candidate.payloadEvidence();
    if (evidence.reservationId() != null
        && !Objects.equals(reservation.getId(), evidence.reservationId())) {
      return false;
    }
    if (evidence.orderKey() != null
        && reservation.getOrderKey() != null
        && !Objects.equals(reservation.getOrderKey(), evidence.orderKey())) {
      return false;
    }
    if (evidence.actionType() != null
        && !MARKETPLACE_ACTION_CODES.contains(evidence.actionType())) {
      return false;
    }
    return true;
  }

  private boolean isBlockingExecutionCandidate(ReservationExecutionCandidateView candidate) {
    return STATUS_CONFIRMED.equals(candidate.status())
        || isAwaitingSignature(candidate.status())
        || STATUS_SIGNED.equals(candidate.status())
        || STATUS_PENDING_ONCHAIN.equals(candidate.status())
        || TRANSACTION_SUCCEEDED.equals(candidate.transactionStatus())
        || TRANSACTION_UNCONFIRMED.equals(candidate.transactionStatus());
  }

  private boolean isBlockingExecutionState(ReservationExecutionStateView state) {
    return state != null
        && (STATUS_CONFIRMED.equals(state.status())
            || isAwaitingSignature(state)
            || STATUS_SIGNED.equals(state.status())
            || STATUS_PENDING_ONCHAIN.equals(state.status())
            || TRANSACTION_SUCCEEDED.equals(state.transactionStatus())
            || TRANSACTION_UNCONFIRMED.equals(state.transactionStatus()));
  }

  private boolean isAwaitingSignature(ReservationExecutionStateView state) {
    return state != null && isAwaitingSignature(state.status());
  }

  private boolean isAwaitingSignature(String status) {
    return STATUS_AWAITING_SIGNATURE.equals(status);
  }

  private boolean matchesAction(ReservationEscrowAction action, String executionActionType) {
    return action != null && actionCode(action).equals(executionActionType);
  }

  private String actionCode(ReservationEscrowAction action) {
    return switch (action) {
      case PURCHASE -> "MARKETPLACE_CLASS_PURCHASE";
      case BUYER_CANCEL, TRAINER_REJECT -> "MARKETPLACE_CLASS_CANCEL";
      case BUYER_CONFIRM -> "MARKETPLACE_CLASS_CONFIRM";
      case DEADLINE_REFUND -> "MARKETPLACE_CLASS_EXPIRED_REFUND";
      case ADMIN_REFUND -> "MARKETPLACE_ADMIN_REFUND";
      case ADMIN_SETTLE -> "MARKETPLACE_ADMIN_SETTLE";
    };
  }
}
