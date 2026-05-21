package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import java.math.BigInteger;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.global.error.marketplace.ReservationNotFoundException;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceAdminEscrowReviewResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceAdminExecutionAttemptView;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceAdminExecutionAuthorityView;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceAdminExecutionPhase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceAdminParticipantView;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceAdminReasonReviewOption;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceAdminReviewValidationCode;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceAdminReviewValidationItem;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceAdminTokenView;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationExecutionStateView;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationActionStatePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationEscrowPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationExecutionStatePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.MarketplaceReservationActionState;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.MarketplaceReservationEscrow;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.Reservation;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationActionStateStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowFlow;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;

final class MarketplaceAdminReviewSupport {

  private MarketplaceAdminReviewSupport() {}

  static Context load(
      Long reservationId,
      LoadReservationPort loadReservationPort,
      LoadReservationEscrowPort loadReservationEscrowPort,
      LoadReservationActionStatePort loadReservationActionStatePort) {
    Reservation reservation =
        loadReservationPort
            .findById(reservationId)
            .orElseThrow(() -> new ReservationNotFoundException(reservationId));
    Optional<MarketplaceReservationEscrow> escrow =
        loadReservationEscrowPort.findByReservationId(reservationId);
    Optional<MarketplaceReservationActionState> latestAttempt =
        loadReservationActionStatePort.findLatestByReservationId(reservationId);
    return new Context(reservation, escrow.orElse(null), latestAttempt.orElse(null));
  }

  static MarketplaceAdminEscrowReviewResult result(
      Context context,
      Clock clock,
      String pollingEndpoint,
      List<MarketplaceAdminReasonReviewOption> reasonOptions,
      List<MarketplaceAdminReviewValidationItem> baseItems,
      LoadReservationExecutionStatePort loadReservationExecutionStatePort) {
    Reservation reservation = context.reservation();
    LocalDateTime now = LocalDateTime.now(clock);
    MarketplaceAdminReviewValidationCode baseBlockingCode = firstBlockingCode(baseItems);
    MarketplaceAdminExecutionAttemptView activeAttempt =
        activeAttempt(context.latestAttempt(), loadReservationExecutionStatePort);
    MarketplaceAdminExecutionAttemptView lastAttempt = lastAttempt(context.latestAttempt());
    MarketplaceAdminExecutionPhase phase = phase(reservation, activeAttempt, lastAttempt);
    return new MarketplaceAdminEscrowReviewResult(
        reservation.getId(),
        baseBlockingCode == null
            && reasonOptions.stream().anyMatch(MarketplaceAdminReasonReviewOption::processable),
        baseBlockingCode,
        baseBlockingCode == null ? null : baseBlockingCode.name(),
        reservation.getStatus(),
        reservation.getEffectiveEscrowStatus(),
        new MarketplaceAdminParticipantView(
            reservation.getUserId(), reservation.getBuyerWalletAddress()),
        new MarketplaceAdminParticipantView(
            reservation.getTrainerId(), reservation.getTrainerWalletAddress()),
        new MarketplaceAdminTokenView(reservation.getTokenAddress(), amount(reservation), "MZTK"),
        now,
        null,
        reservation.getVersion(),
        phase,
        nextPollAfterMs(phase),
        pollingEndpoint,
        reservation.getTxHash(),
        MarketplaceAdminExecutionAuthorityView.serverRelayerOnly(),
        activeAttempt,
        activeAttempt == null ? lastAttempt : null,
        baseItems,
        reasonOptions);
  }

  static List<MarketplaceAdminReviewValidationItem> baseItems(
      Context context, ReservationStatus expectedStatus) {
    List<MarketplaceAdminReviewValidationItem> items = new ArrayList<>();
    Reservation reservation = context.reservation();
    if (reservation.getEffectiveEscrowFlow() != ReservationEscrowFlow.USER_EIP7702) {
      items.add(
          MarketplaceAdminReviewValidationItem.blocking(
              MarketplaceAdminReviewValidationCode.RESERVATION_NOT_USER_EIP7702,
              "reservation must use USER_EIP7702 escrow flow"));
    }
    if (reservation.getStatus() != expectedStatus) {
      items.add(
          MarketplaceAdminReviewValidationItem.blocking(
              MarketplaceAdminReviewValidationCode.INVALID_LOCAL_STATUS,
              "reservation status is not executable for this admin action"));
    }
    if (reservation.getEffectiveEscrowStatus() != ReservationEscrowStatus.LOCKED) {
      items.add(
          MarketplaceAdminReviewValidationItem.blocking(
              MarketplaceAdminReviewValidationCode.ESCROW_NOT_LOCKED,
              "reservation escrow must be locked"));
    }
    MarketplaceReservationActionState latest = context.latestAttempt();
    if (reservation.getCurrentExecutionIntentPublicId() != null
        || (latest != null && latest.getStatus() != null && latest.getStatus().isActive())) {
      items.add(
          MarketplaceAdminReviewValidationItem.blocking(
              MarketplaceAdminReviewValidationCode.ACTIVE_EXECUTION_EXISTS,
              "reservation already has an active execution"));
    }
    if (items.isEmpty()) {
      items.add(
          MarketplaceAdminReviewValidationItem.info(
              MarketplaceAdminReviewValidationCode.OK, "reservation is locally executable"));
    }
    return List.copyOf(items);
  }

  static MarketplaceAdminReviewValidationCode firstBlockingCode(
      List<MarketplaceAdminReviewValidationItem> items) {
    return items.stream()
        .filter(MarketplaceAdminReviewValidationItem::blocking)
        .map(MarketplaceAdminReviewValidationItem::code)
        .findFirst()
        .orElse(null);
  }

  private static MarketplaceAdminExecutionPhase phase(
      Reservation reservation,
      MarketplaceAdminExecutionAttemptView activeAttempt,
      MarketplaceAdminExecutionAttemptView lastAttempt) {
    if (reservation.getStatus() == ReservationStatus.MANUAL_SYNC_REQUIRED) {
      return MarketplaceAdminExecutionPhase.MANUAL_SYNC_REQUIRED;
    }
    if (reservation.getStatus() == ReservationStatus.DEADLINE_SYNC_REQUIRED) {
      return MarketplaceAdminExecutionPhase.DEADLINE_SYNC_REQUIRED;
    }
    if (activeAttempt != null) {
      return activeAttempt.adminExecutionPhase();
    }
    if (reservation.getStatus() == ReservationStatus.TIMEOUT_CANCELLED
        || reservation.getStatus() == ReservationStatus.AUTO_SETTLED) {
      return MarketplaceAdminExecutionPhase.COMPLETED;
    }
    if (lastAttempt != null && lastAttempt.adminExecutionPhase() != null) {
      return lastAttempt.adminExecutionPhase();
    }
    return MarketplaceAdminExecutionPhase.IDLE;
  }

  private static MarketplaceAdminExecutionAttemptView activeAttempt(
      MarketplaceReservationActionState latest,
      LoadReservationExecutionStatePort loadReservationExecutionStatePort) {
    if (latest == null || latest.getStatus() == null || !latest.getStatus().isActive()) {
      return null;
    }
    ReservationExecutionStateView executionState =
        loadExecutionState(latest, loadReservationExecutionStatePort);
    MarketplaceAdminExecutionPhase phase =
        latest.getStatus() == ReservationActionStateStatus.INTENT_BOUND
            ? intentBoundPhase(executionState)
            : MarketplaceAdminExecutionPhase.QUEUED_FOR_SERVER_RELAYER;
    return attemptView(latest, phase, executionState);
  }

  private static MarketplaceAdminExecutionAttemptView lastAttempt(
      MarketplaceReservationActionState latest) {
    if (latest == null || latest.getStatus() == null || latest.getStatus().isActive()) {
      return null;
    }
    MarketplaceAdminExecutionPhase phase =
        switch (latest.getStatus()) {
          case CONFIRMED -> MarketplaceAdminExecutionPhase.COMPLETED;
          case TERMINATED -> MarketplaceAdminExecutionPhase.FAILED_ONCHAIN;
          case STALE -> MarketplaceAdminExecutionPhase.MANUAL_SYNC_REQUIRED;
          default -> MarketplaceAdminExecutionPhase.IDLE;
        };
    return attemptView(latest, phase, null);
  }

  private static MarketplaceAdminExecutionAttemptView attemptView(
      MarketplaceReservationActionState actionState,
      MarketplaceAdminExecutionPhase phase,
      ReservationExecutionStateView executionState) {
    boolean closed = !actionState.getStatus().isActive();
    return new MarketplaceAdminExecutionAttemptView(
        actionState.getId(),
        actionState.getStatus(),
        MarketplaceAdminAttemptFailureStageResolver.resolve(actionState),
        actionState.getExecutionIntentPublicId(),
        executionState == null ? null : executionState.status(),
        phase,
        executionState == null ? null : executionState.txHash(),
        actionState.getErrorReason(),
        actionState.getErrorCode(),
        null,
        actionState.getRetryable(),
        closed ? actionState.getUpdatedAt() : null);
  }

  private static Long nextPollAfterMs(MarketplaceAdminExecutionPhase phase) {
    return switch (phase) {
      case QUEUED_FOR_SERVER_RELAYER, PENDING_ONCHAIN, CONFIRMED_PENDING_LOCAL_SYNC -> 2000L;
      default -> null;
    };
  }

  private static ReservationExecutionStateView loadExecutionState(
      MarketplaceReservationActionState actionState,
      LoadReservationExecutionStatePort loadReservationExecutionStatePort) {
    if (loadReservationExecutionStatePort == null
        || actionState.getExecutionIntentPublicId() == null
        || actionState.getExecutionIntentPublicId().isBlank()) {
      return null;
    }
    try {
      return loadReservationExecutionStatePort.loadState(actionState.getExecutionIntentPublicId());
    } catch (RuntimeException e) {
      return null;
    }
  }

  private static MarketplaceAdminExecutionPhase intentBoundPhase(
      ReservationExecutionStateView executionState) {
    if (executionState == null) {
      return MarketplaceAdminExecutionPhase.PENDING_ONCHAIN;
    }
    if ("CONFIRMED".equals(executionState.status())
        || "SUCCEEDED".equals(executionState.transactionStatus())) {
      return MarketplaceAdminExecutionPhase.CONFIRMED_PENDING_LOCAL_SYNC;
    }
    if ("FAILED_ONCHAIN".equals(executionState.status())) {
      return MarketplaceAdminExecutionPhase.FAILED_ONCHAIN;
    }
    if ("EXPIRED".equals(executionState.status())
        || "CANCELED".equals(executionState.status())
        || "NONCE_STALE".equals(executionState.status())) {
      return MarketplaceAdminExecutionPhase.EXPIRED;
    }
    return MarketplaceAdminExecutionPhase.PENDING_ONCHAIN;
  }

  private static BigInteger amount(Reservation reservation) {
    if (reservation.getPriceBaseUnits() == null || reservation.getPriceBaseUnits().isBlank()) {
      return null;
    }
    return new BigInteger(reservation.getPriceBaseUnits());
  }

  record Context(
      Reservation reservation,
      MarketplaceReservationEscrow escrow,
      MarketplaceReservationActionState latestAttempt) {}
}
