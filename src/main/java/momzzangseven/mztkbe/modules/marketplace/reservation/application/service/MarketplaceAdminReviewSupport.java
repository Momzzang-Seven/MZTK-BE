package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import java.math.BigInteger;
import java.time.Clock;
import java.time.Instant;
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
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationEscrowOrderView;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationExecutionStateView;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadMarketplaceAdminExecutionAuthorityPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationActionStatePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationEscrowOrderPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationEscrowPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationExecutionCandidatePort;
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

  private static final String RECONCILING_ERROR_CODE = "RECONCILING";

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
      PreflightResult preflight,
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
        preflight == null ? null : preflight.chainCheckedAt(),
        reservation.getVersion(),
        phase,
        nextPollAfterMs(phase, activeAttempt),
        pollingEndpoint,
        reservation.getTxHash(),
        preflight == null
            ? MarketplaceAdminExecutionAuthorityView.serverRelayerOnly()
            : preflight.authority(),
        activeAttempt,
        activeAttempt == null ? lastAttempt : null,
        baseItems,
        reasonOptions);
  }

  static PreflightResult preflight(
      Context context,
      Clock clock,
      LoadReservationEscrowOrderPort loadReservationEscrowOrderPort,
      LoadMarketplaceAdminExecutionAuthorityPort loadMarketplaceAdminExecutionAuthorityPort) {
    List<MarketplaceAdminReviewValidationItem> items = new ArrayList<>();
    LocalDateTime chainCheckedAt = null;
    if (loadReservationEscrowOrderPort != null) {
      chainCheckedAt = LocalDateTime.now(clock);
      items.addAll(
          chainPreflightItems(context.reservation(), loadReservationEscrowOrderPort, clock));
    }
    MarketplaceAdminExecutionAuthorityView authority =
        loadAuthority(loadMarketplaceAdminExecutionAuthorityPort);
    if (loadMarketplaceAdminExecutionAuthorityPort != null) {
      items.addAll(authorityPreflightItems(authority));
    }
    return new PreflightResult(chainCheckedAt, authority, items);
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

  static List<MarketplaceAdminReviewValidationItem> unresolvedExecutionItems(
      Context context,
      LoadReservationExecutionStatePort loadReservationExecutionStatePort,
      LoadReservationExecutionCandidatePort loadReservationExecutionCandidatePort) {
    if (loadReservationExecutionStatePort == null
        || loadReservationExecutionCandidatePort == null) {
      return List.of();
    }
    ReservationExecutionCandidateGuard guard =
        new ReservationExecutionCandidateGuard(
            loadReservationExecutionStatePort, loadReservationExecutionCandidatePort);
    if (!guard.hasBlockingExecutionForAnyMarketplaceAction(context.reservation())) {
      return List.of();
    }
    return List.of(
        MarketplaceAdminReviewValidationItem.blocking(
            MarketplaceAdminReviewValidationCode.ACTIVE_EXECUTION_EXISTS,
            "reservation already has an unresolved marketplace execution"));
  }

  static MarketplaceAdminReviewValidationCode firstBlockingCode(
      List<MarketplaceAdminReviewValidationItem> items) {
    return items.stream()
        .filter(MarketplaceAdminReviewValidationItem::blocking)
        .map(MarketplaceAdminReviewValidationItem::code)
        .findFirst()
        .orElse(null);
  }

  private static List<MarketplaceAdminReviewValidationItem> chainPreflightItems(
      Reservation reservation,
      LoadReservationEscrowOrderPort loadReservationEscrowOrderPort,
      Clock clock) {
    if (reservation.getOrderKey() == null || reservation.getOrderKey().isBlank()) {
      return List.of(
          MarketplaceAdminReviewValidationItem.blocking(
              MarketplaceAdminReviewValidationCode.CHAIN_ORDER_ABSENT,
              "marketplace chain order key is missing"));
    }
    ReservationEscrowOrderView order;
    try {
      order = loadReservationEscrowOrderPort.getOrder(reservation.getOrderKey());
    } catch (RuntimeException e) {
      return List.of(
          MarketplaceAdminReviewValidationItem.blocking(
              MarketplaceAdminReviewValidationCode.CHAIN_LOOKUP_FAILED,
              "marketplace chain order lookup failed"));
    }
    MarketplaceAdminReviewValidationCode code = chainBlockingCode(order, clock);
    if (code == null) {
      return List.of();
    }
    return List.of(MarketplaceAdminReviewValidationItem.blocking(code, code.name()));
  }

  private static MarketplaceAdminReviewValidationCode chainBlockingCode(
      ReservationEscrowOrderView order, Clock clock) {
    if (order == null || order.isAbsent()) {
      return MarketplaceAdminReviewValidationCode.CHAIN_ORDER_ABSENT;
    }
    if (order.state() == ReservationEscrowOrderView.STATE_CREATED) {
      if (order.deadlineEpochSeconds() != null
          && !LocalDateTime.ofInstant(
                  Instant.ofEpochSecond(order.deadlineEpochSeconds()), clock.getZone())
              .isAfter(LocalDateTime.now(clock))) {
        return MarketplaceAdminReviewValidationCode.CONTRACT_DEADLINE_EXPIRED;
      }
      return null;
    }
    return switch (order.state()) {
      case ReservationEscrowOrderView.STATE_ADMIN_REFUNDED ->
          MarketplaceAdminReviewValidationCode.CHAIN_ORDER_ALREADY_REFUNDED;
      case ReservationEscrowOrderView.STATE_ADMIN_SETTLED,
              ReservationEscrowOrderView.STATE_CONFIRMED ->
          MarketplaceAdminReviewValidationCode.CHAIN_ORDER_ALREADY_SETTLED;
      case ReservationEscrowOrderView.STATE_DEADLINE_REFUNDED ->
          MarketplaceAdminReviewValidationCode.CHAIN_MISMATCH_REQUIRES_SYNC;
      case ReservationEscrowOrderView.STATE_CANCELLED ->
          MarketplaceAdminReviewValidationCode.CHAIN_MISMATCH_REQUIRES_SYNC;
      default -> MarketplaceAdminReviewValidationCode.CHAIN_ORDER_NOT_CREATED;
    };
  }

  private static MarketplaceAdminExecutionAuthorityView loadAuthority(
      LoadMarketplaceAdminExecutionAuthorityPort loadMarketplaceAdminExecutionAuthorityPort) {
    if (loadMarketplaceAdminExecutionAuthorityPort == null) {
      return MarketplaceAdminExecutionAuthorityView.serverRelayerOnly();
    }
    try {
      MarketplaceAdminExecutionAuthorityView authority =
          loadMarketplaceAdminExecutionAuthorityPort.load();
      return authority == null
          ? MarketplaceAdminExecutionAuthorityView.serverRelayerOnly()
          : authority;
    } catch (RuntimeException e) {
      return MarketplaceAdminExecutionAuthorityView.serverRelayerOnly();
    }
  }

  private static List<MarketplaceAdminReviewValidationItem> authorityPreflightItems(
      MarketplaceAdminExecutionAuthorityView authority) {
    if (!authority.serverSignerAvailable()) {
      return List.of(
          MarketplaceAdminReviewValidationItem.blocking(
              MarketplaceAdminReviewValidationCode.SERVER_SIGNER_UNAVAILABLE,
              "marketplace admin server signer is unavailable"));
    }
    if (authority.relayerRegistrationCheckFailed()) {
      return List.of(
          MarketplaceAdminReviewValidationItem.blocking(
              MarketplaceAdminReviewValidationCode.RELAYER_REGISTRATION_CHECK_FAILED,
              "marketplace admin relayer registration check failed"));
    }
    if (!authority.relayerRegistered()) {
      return List.of(
          MarketplaceAdminReviewValidationItem.blocking(
              MarketplaceAdminReviewValidationCode.RELAYER_NOT_REGISTERED,
              "marketplace admin server signer is not registered as relayer"));
    }
    return List.of();
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
          case TERMINATED -> terminatedPhase(latest.getErrorCode());
          case STALE -> stalePhase(latest.getErrorCode());
          default -> MarketplaceAdminExecutionPhase.IDLE;
        };
    return attemptView(latest, phase, null);
  }

  private static MarketplaceAdminExecutionPhase terminatedPhase(String errorCode) {
    if ("EXPIRED".equals(errorCode)
        || "CANCELED".equals(errorCode)
        || "NONCE_STALE".equals(errorCode)) {
      return MarketplaceAdminExecutionPhase.EXPIRED;
    }
    return MarketplaceAdminExecutionPhase.FAILED_ONCHAIN;
  }

  private static MarketplaceAdminExecutionPhase stalePhase(String errorCode) {
    MarketplaceAdminReviewValidationCode code = parseValidationCode(errorCode);
    if (code == MarketplaceAdminReviewValidationCode.CONTRACT_DEADLINE_EXPIRED) {
      return MarketplaceAdminExecutionPhase.DEADLINE_SYNC_REQUIRED;
    }
    if (code == MarketplaceAdminReviewValidationCode.CHAIN_ORDER_ABSENT
        || code == MarketplaceAdminReviewValidationCode.CHAIN_ORDER_NOT_CREATED
        || code == MarketplaceAdminReviewValidationCode.CHAIN_ORDER_ALREADY_REFUNDED
        || code == MarketplaceAdminReviewValidationCode.CHAIN_ORDER_ALREADY_SETTLED
        || code == MarketplaceAdminReviewValidationCode.CHAIN_MISMATCH_REQUIRES_SYNC
        || code == MarketplaceAdminReviewValidationCode.MANUAL_SYNC_REQUIRED) {
      return MarketplaceAdminExecutionPhase.MANUAL_SYNC_REQUIRED;
    }
    return MarketplaceAdminExecutionPhase.IDLE;
  }

  private static MarketplaceAdminReviewValidationCode parseValidationCode(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return MarketplaceAdminReviewValidationCode.valueOf(value);
    } catch (IllegalArgumentException ignored) {
      return null;
    }
  }

  private static MarketplaceAdminExecutionAttemptView attemptView(
      MarketplaceReservationActionState actionState,
      MarketplaceAdminExecutionPhase phase,
      ReservationExecutionStateView executionState) {
    boolean closed = !actionState.getStatus().isActive();
    String errorCode =
        RECONCILING_ERROR_CODE.equals(actionState.getErrorCode())
            ? null
            : actionState.getErrorCode();
    String errorReason =
        RECONCILING_ERROR_CODE.equals(actionState.getErrorCode())
            ? null
            : actionState.getErrorReason();
    return new MarketplaceAdminExecutionAttemptView(
        actionState.getId(),
        actionState.getStatus(),
        RECONCILING_ERROR_CODE.equals(actionState.getErrorCode())
            ? null
            : MarketplaceAdminAttemptFailureStageResolver.resolve(actionState),
        actionState.getExecutionIntentPublicId(),
        executionState == null ? null : executionState.status(),
        phase,
        executionState == null ? null : executionState.txHash(),
        errorReason,
        errorCode,
        evidenceErrorCode(errorCode),
        actionState.getRetryable(),
        closed ? actionState.getUpdatedAt() : null);
  }

  private static Long nextPollAfterMs(
      MarketplaceAdminExecutionPhase phase, MarketplaceAdminExecutionAttemptView activeAttempt) {
    return switch (phase) {
      case QUEUED_FOR_SERVER_RELAYER, PENDING_ONCHAIN, CONFIRMED_PENDING_LOCAL_SYNC -> 2000L;
      case FAILED_ONCHAIN, EXPIRED -> activeAttempt == null ? null : 2000L;
      default -> null;
    };
  }

  private static String evidenceErrorCode(String errorCode) {
    if ("CHAIN_ORDER_LOOKUP_FAILED".equals(errorCode)
        || "CHAIN_ORDER_EVIDENCE_UNAVAILABLE".equals(errorCode)
        || "CHAIN_ORDER_STATE_UNSUPPORTED".equals(errorCode)
        || "EVIDENCE_UNAVAILABLE".equals(errorCode)) {
      return errorCode;
    }
    return null;
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
    if ("FAILED_ONCHAIN".equals(executionState.status())
        || "FAILED_ONCHAIN".equals(executionState.transactionStatus())) {
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

  record PreflightResult(
      LocalDateTime chainCheckedAt,
      MarketplaceAdminExecutionAuthorityView authority,
      List<MarketplaceAdminReviewValidationItem> validationItems) {
    PreflightResult {
      authority =
          authority == null
              ? MarketplaceAdminExecutionAuthorityView.serverRelayerOnly()
              : authority;
      validationItems = validationItems == null ? List.of() : List.copyOf(validationItems);
    }

    PreflightResult withOperatorAuthority(boolean canEarlySettle, boolean canManualRefund) {
      return new PreflightResult(
          chainCheckedAt,
          authority.withOperatorAuthority(canEarlySettle, canManualRefund),
          validationItems);
    }
  }
}
