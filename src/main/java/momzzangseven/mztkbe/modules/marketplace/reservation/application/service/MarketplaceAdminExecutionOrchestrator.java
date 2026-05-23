package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import java.math.BigInteger;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;
import momzzangseven.mztkbe.global.error.marketplace.MarketplaceReservationStateException;
import momzzangseven.mztkbe.global.error.marketplace.ReservationNotFoundException;
import momzzangseven.mztkbe.global.error.web3.Web3PreparationFailureClassifier;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceAdminExecutionPhase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceAdminExecutionResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceAdminRefundReasonCode;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceAdminReviewValidationCode;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceAdminSettleReasonCode;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.PrepareMarketplaceAdminEscrowCommand;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationAdminExecutionDraft;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationEscrowOrderView;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.SubmitMarketplaceAdminEscrowResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.BindReservationActionStatePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.BuildMarketplaceAdminReservationExecutionPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationActionStatePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationEscrowOrderPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationEscrowPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationExecutionCandidatePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationExecutionStatePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.RunReservationTransactionPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SaveReservationActionStatePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SaveReservationEscrowPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SaveReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SubmitMarketplaceAdminReservationExecutionPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.MarketplaceReservationActionState;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.MarketplaceReservationEscrow;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.Reservation;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationActionRequestSource;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationActionStateStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowAction;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowActorType;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowFlow;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationTerminalResolvedBy;

@Slf4j
public class MarketplaceAdminExecutionOrchestrator {

  private static final String AUTHORITY_MODEL = "SERVER_RELAYER_ONLY";
  private static final long PREPARATION_TTL_MINUTES = 10;

  private final LoadReservationPort loadReservationPort;
  private final SaveReservationPort saveReservationPort;
  private final LoadReservationEscrowPort loadReservationEscrowPort;
  private final LoadReservationEscrowOrderPort loadReservationEscrowOrderPort;
  private final SaveReservationEscrowPort saveReservationEscrowPort;
  private final LoadReservationActionStatePort loadReservationActionStatePort;
  private final SaveReservationActionStatePort saveReservationActionStatePort;
  private final BindReservationActionStatePort bindReservationActionStatePort;
  private final BuildMarketplaceAdminReservationExecutionPort buildExecutionPort;
  private final SubmitMarketplaceAdminReservationExecutionPort submitExecutionPort;
  private final ReservationExecutionCandidateGuard executionCandidateGuard;
  private final RunReservationTransactionPort transactionPort;
  private final Clock clock;

  public MarketplaceAdminExecutionOrchestrator(
      LoadReservationPort loadReservationPort,
      SaveReservationPort saveReservationPort,
      LoadReservationEscrowPort loadReservationEscrowPort,
      LoadReservationEscrowOrderPort loadReservationEscrowOrderPort,
      SaveReservationEscrowPort saveReservationEscrowPort,
      LoadReservationActionStatePort loadReservationActionStatePort,
      SaveReservationActionStatePort saveReservationActionStatePort,
      BindReservationActionStatePort bindReservationActionStatePort,
      BuildMarketplaceAdminReservationExecutionPort buildExecutionPort,
      SubmitMarketplaceAdminReservationExecutionPort submitExecutionPort,
      LoadReservationExecutionStatePort loadReservationExecutionStatePort,
      LoadReservationExecutionCandidatePort loadReservationExecutionCandidatePort,
      RunReservationTransactionPort transactionPort,
      Clock clock) {
    this.loadReservationPort = loadReservationPort;
    this.saveReservationPort = saveReservationPort;
    this.loadReservationEscrowPort = loadReservationEscrowPort;
    this.loadReservationEscrowOrderPort = loadReservationEscrowOrderPort;
    this.saveReservationEscrowPort = saveReservationEscrowPort;
    this.loadReservationActionStatePort = loadReservationActionStatePort;
    this.saveReservationActionStatePort = saveReservationActionStatePort;
    this.bindReservationActionStatePort = bindReservationActionStatePort;
    this.buildExecutionPort = buildExecutionPort;
    this.submitExecutionPort = submitExecutionPort;
    this.executionCandidateGuard =
        loadReservationExecutionStatePort == null || loadReservationExecutionCandidatePort == null
            ? null
            : new ReservationExecutionCandidateGuard(
                loadReservationExecutionStatePort, loadReservationExecutionCandidatePort);
    this.transactionPort = transactionPort;
    this.clock = clock;
  }

  public MarketplaceAdminExecutionResult executeRefund(
      Long operatorUserId,
      MarketplaceAdminRefundReasonCode reasonCode,
      String memo,
      boolean canManualRefund,
      Long reservationId) {
    validateManualRefundReason(reasonCode, canManualRefund);
    AdminPhaseA phaseA =
        transactionPort.requiresNew(
            () ->
                preparePhaseA(
                    ReservationEscrowAction.ADMIN_REFUND,
                    ReservationActionRequestSource.MANUAL_ADMIN,
                    operatorUserId,
                    null,
                    reasonCode.name(),
                    memo,
                    reservationId));
    return buildAndBind(phaseA, "/refund-review");
  }

  public MarketplaceAdminExecutionResult executeSettlement(
      Long operatorUserId,
      MarketplaceAdminSettleReasonCode reasonCode,
      String memo,
      boolean canEarlySettle,
      Long reservationId) {
    validateManualSettlementReason(reasonCode, canEarlySettle);
    AdminPhaseA phaseA =
        transactionPort.requiresNew(
            () ->
                preparePhaseA(
                    ReservationEscrowAction.ADMIN_SETTLE,
                    ReservationActionRequestSource.MANUAL_ADMIN,
                    operatorUserId,
                    null,
                    reasonCode.name(),
                    memo,
                    reservationId));
    return buildAndBind(phaseA, "/settlement-review");
  }

  public MarketplaceAdminExecutionResult executeSchedulerRefund(
      String schedulerRunId, MarketplaceAdminRefundReasonCode reasonCode, Long reservationId) {
    validateSchedulerRefundReason(reasonCode);
    AdminPhaseA phaseA =
        transactionPort.requiresNew(
            () ->
                preparePhaseA(
                    ReservationEscrowAction.ADMIN_REFUND,
                    ReservationActionRequestSource.SCHEDULER,
                    null,
                    schedulerRunId,
                    reasonCode.name(),
                    null,
                    reservationId));
    return buildAndBind(phaseA, "/refund-review");
  }

  public MarketplaceAdminExecutionResult executeSchedulerSettlement(
      String schedulerRunId, MarketplaceAdminSettleReasonCode reasonCode, Long reservationId) {
    validateSchedulerSettlementReason(reasonCode);
    AdminPhaseA phaseA =
        transactionPort.requiresNew(
            () ->
                preparePhaseA(
                    ReservationEscrowAction.ADMIN_SETTLE,
                    ReservationActionRequestSource.SCHEDULER,
                    null,
                    schedulerRunId,
                    reasonCode.name(),
                    null,
                    reservationId));
    return buildAndBind(phaseA, "/settlement-review");
  }

  private MarketplaceAdminExecutionResult buildAndBind(AdminPhaseA phaseA, String pollingSuffix) {
    ReservationAdminExecutionDraft draft;
    try {
      PhaseBChainInspection chainInspection =
          transactionPort.notSupported(() -> inspectPhaseBChain(phaseA));
      if (!chainInspection.proceed()) {
        AdminPhaseBChainMismatchException mismatch =
            new AdminPhaseBChainMismatchException(chainInspection);
        cleanupUnboundPreparation(phaseA, ReservationActionStateStatus.STALE, mismatch);
        throw conflict(chainInspection.code());
      }
      draft =
          transactionPort.notSupported(
              () ->
                  phaseA.action() == ReservationEscrowAction.ADMIN_REFUND
                      ? buildExecutionPort.buildRefund(phaseA.prepareCommand())
                      : buildExecutionPort.buildSettlement(phaseA.prepareCommand()));
    } catch (AdminPhaseBTransientException e) {
      cleanupUnboundPreparation(phaseA, ReservationActionStateStatus.PREPARATION_FAILED, e);
      throw e;
    } catch (RuntimeException e) {
      cleanupUnboundPreparation(phaseA, ReservationActionStateStatus.PREPARATION_FAILED, e);
      throw e;
    }

    try {
      return transactionPort.requiresNew(() -> bindPhaseC(phaseA, draft, pollingSuffix));
    } catch (AdminPhaseCStaleException e) {
      cleanupUnboundPreparation(phaseA, ReservationActionStateStatus.STALE, e);
      throw e;
    } catch (RuntimeException e) {
      cleanupUnboundPreparation(phaseA, phaseCFailureStatus(e), e);
      throw e;
    }
  }

  private AdminPhaseA preparePhaseA(
      ReservationEscrowAction action,
      ReservationActionRequestSource source,
      Long operatorUserId,
      String schedulerRunId,
      String reasonCode,
      String memo,
      Long reservationId) {
    Reservation reservation =
        loadReservationPort
            .findByIdWithLock(reservationId)
            .orElseThrow(() -> new ReservationNotFoundException(reservationId));
    MarketplaceReservationEscrow escrow =
        loadReservationEscrowPort
            .findByReservationIdWithLock(reservationId)
            .orElseThrow(
                () ->
                    new MarketplaceReservationStateException(
                        ErrorCode.MARKETPLACE_RESERVATION_INVALID_STATUS,
                        "marketplace reservation escrow projection is required"));
    MarketplaceReservationActionState latest =
        loadReservationActionStatePort
            .findLatestByReservationIdWithLock(reservationId)
            .orElse(null);
    validateBaseExecutable(action, reservation, latest);
    validateReasonWindow(action, reasonCode, reservation);

    String attemptToken = UUID.randomUUID().toString();
    LocalDateTime expiresAt = LocalDateTime.now(clock).plusMinutes(PREPARATION_TTL_MINUTES);
    Reservation preparedReservation =
        action == ReservationEscrowAction.ADMIN_REFUND
            ? reservation.beginAdminRefundPending(attemptToken, expiresAt)
            : reservation.beginAdminSettlePending(attemptToken, expiresAt);
    Reservation savedReservation = saveReservationPort.save(preparedReservation);
    ReservationEscrowStatus preparedEscrowStatus =
        action == ReservationEscrowAction.ADMIN_REFUND
            ? ReservationEscrowStatus.ADMIN_REFUND_PENDING
            : ReservationEscrowStatus.ADMIN_SETTLE_PENDING;
    MarketplaceReservationEscrow savedEscrow =
        saveReservationEscrowPort.save(
            escrow.toBuilder().escrowStatus(preparedEscrowStatus).build());
    int attemptNo = latest == null || latest.getAttemptNo() == null ? 1 : latest.getAttemptNo() + 1;
    String rootKey = rootIdempotencyKey(action, reservationId, source, reasonCode);
    MarketplaceReservationActionState actionState =
        saveReservationActionStatePort.save(
            MarketplaceReservationActionState.builder()
                .reservationId(savedReservation.getId())
                .escrowId(savedEscrow.getId())
                .actionType(action)
                .actorType(
                    source == ReservationActionRequestSource.MANUAL_ADMIN
                        ? ReservationEscrowActorType.ADMIN
                        : ReservationEscrowActorType.SYSTEM)
                .actorUserId(operatorUserId)
                .requestSource(source)
                .attemptNo(attemptNo)
                .attemptToken(attemptToken)
                .status(ReservationActionStateStatus.PREPARING)
                .rootIdempotencyKey(rootKey)
                .expectedReservationVersion(savedReservation.getVersion())
                .expectedReservationStatus(savedReservation.getStatus())
                .expectedEscrowStatus(savedReservation.getEffectiveEscrowStatus())
                .priorReservationStatus(reservation.getStatus())
                .priorEscrowStatus(reservation.getEffectiveEscrowStatus())
                .preparationExpiresAt(expiresAt)
                .actionReason(reasonCode)
                .reasonCode(reasonCode)
                .memo(memo)
                .retryable(false)
                .build());
    return new AdminPhaseA(
        action,
        savedReservation,
        savedEscrow,
        actionState,
        prepareCommand(
            action,
            source,
            operatorUserId,
            schedulerRunId,
            reasonCode,
            memo,
            rootKey,
            savedReservation,
            savedEscrow,
            actionState));
  }

  private MarketplaceAdminExecutionResult bindPhaseC(
      AdminPhaseA phaseA, ReservationAdminExecutionDraft draft, String pollingSuffix) {
    Reservation reservation =
        loadReservationPort
            .findByIdWithLock(phaseA.reservation().getId())
            .orElseThrow(() -> new ReservationNotFoundException(phaseA.reservation().getId()));
    MarketplaceReservationEscrow escrow =
        loadReservationEscrowPort
            .findByReservationIdWithLock(phaseA.reservation().getId())
            .orElseThrow(
                () ->
                    new MarketplaceReservationStateException(
                        ErrorCode.MARKETPLACE_RESERVATION_INVALID_STATUS,
                        "marketplace reservation escrow projection is required"));
    MarketplaceReservationActionState actionState =
        loadReservationActionStatePort
            .findByIdWithLock(phaseA.actionState().getId())
            .orElseThrow(
                () ->
                    new MarketplaceReservationStateException(
                        ErrorCode.MARKETPLACE_ACTIVE_EXECUTION_CONFLICT,
                        "admin action state disappeared before bind"));
    validatePreparedSnapshot(phaseA, actionState, reservation, escrow);

    SubmitMarketplaceAdminEscrowResult submitted = submitExecutionPort.submit(draft);
    MarketplaceReservationActionState boundActionState =
        bindReservationActionStatePort
            .bindExecutionIntent(
                actionState.getId(), actionState.getAttemptToken(), submitted.executionIntentId())
            .orElseThrow(
                () ->
                    new MarketplaceReservationStateException(
                        ErrorCode.MARKETPLACE_ACTIVE_EXECUTION_CONFLICT,
                        "admin action state changed before execution intent bind"));
    Reservation boundReservation =
        saveReservationPort.save(
            reservation.bindAdminPendingExecutionIntent(submitted.executionIntentId()));
    saveReservationEscrowPort.save(
        escrow.toBuilder().escrowStatus(phaseA.escrow().getEscrowStatus()).build());

    log.info(
        "Marketplace admin execution prepared: reservationId={}, action={}, actionStateId={}, intentId={}",
        boundReservation.getId(),
        phaseA.action(),
        boundActionState.getId(),
        submitted.executionIntentId());
    return new MarketplaceAdminExecutionResult(
        boundReservation.getId(),
        marketplaceActionType(phaseA.action()),
        boundReservation.getOrderKey(),
        boundReservation.getStatus(),
        boundReservation.getEffectiveEscrowStatus(),
        new MarketplaceAdminExecutionResult.ExecutionIntent(
            submitted.executionIntentId(),
            submitted.executionIntentStatus(),
            submitted.expiresAt()),
        new MarketplaceAdminExecutionResult.Execution(
            submitted.executionMode(), false, AUTHORITY_MODEL),
        MarketplaceAdminExecutionPhase.QUEUED_FOR_SERVER_RELAYER,
        2000L,
        "/admin/web3/marketplace/reservations/" + boundReservation.getId() + pollingSuffix,
        submitted.existing());
  }

  private void cleanupUnboundPreparation(
      AdminPhaseA phaseA, ReservationActionStateStatus terminalStatus, RuntimeException cause) {
    transactionPort.requiresNew(
        () -> {
          Reservation reservation =
              loadReservationPort.findByIdWithLock(phaseA.reservation().getId()).orElse(null);
          MarketplaceReservationEscrow escrow =
              loadReservationEscrowPort
                  .findByReservationIdWithLock(phaseA.reservation().getId())
                  .orElse(null);
          MarketplaceReservationActionState actionState =
              loadReservationActionStatePort
                  .findByIdWithLock(phaseA.actionState().getId())
                  .orElse(null);
          if (actionState == null
              || actionState.getStatus() != ReservationActionStateStatus.PREPARING
              || !phaseA.actionState().getAttemptToken().equals(actionState.getAttemptToken())) {
            return null;
          }
          if (cause instanceof AdminPhaseBChainMismatchException mismatch) {
            syncAuthoritativePhaseBOutcome(actionState, mismatch.inspection(), reservation, escrow);
          } else {
            rollbackUnboundPreparation(actionState, reservation, escrow);
          }
          saveReservationActionStatePort.save(
              actionState.toBuilder()
                  .status(terminalStatus)
                  .retryable(
                      terminalStatus == ReservationActionStateStatus.PREPARATION_FAILED
                          && Web3PreparationFailureClassifier.isRetryable(cause))
                  .errorCode(errorCode(cause))
                  .errorReason(cause.getMessage())
                  .build());
          return null;
        });
  }

  private void rollbackUnboundPreparation(
      MarketplaceReservationActionState actionState,
      Reservation reservation,
      MarketplaceReservationEscrow escrow) {
    if (reservation != null) {
      saveReservationPort.save(reservation.rollbackToPriorState());
    }
    if (escrow != null) {
      saveReservationEscrowPort.save(
          escrow.toBuilder().escrowStatus(actionState.getPriorEscrowStatus()).build());
    }
  }

  private void syncAuthoritativePhaseBOutcome(
      MarketplaceReservationActionState actionState,
      PhaseBChainInspection inspection,
      Reservation reservation,
      MarketplaceReservationEscrow escrow) {
    if (reservation == null) {
      throw new ReservationNotFoundException(actionState.getReservationId());
    }
    Reservation updated = inspection.applyTo(reservation);
    Reservation saved = saveReservationPort.save(updated);
    if (escrow != null) {
      saveReservationEscrowPort.save(
          escrow.toBuilder()
              .escrowStatus(saved.getEffectiveEscrowStatus())
              .contractDeadlineEpochSeconds(saved.getContractDeadlineEpochSeconds())
              .contractDeadlineAt(saved.getContractDeadlineAt())
              .lastFailureCode(inspection.code().name())
              .lastFailureMessage(inspection.message())
              .build());
    }
  }

  private PhaseBChainInspection inspectPhaseBChain(AdminPhaseA phaseA) {
    ReservationEscrowOrderView order;
    try {
      order = loadReservationEscrowOrderPort.getOrder(phaseA.reservation().getOrderKey());
    } catch (RuntimeException e) {
      throw new AdminPhaseBTransientException(
          MarketplaceAdminReviewValidationCode.CHAIN_LOOKUP_FAILED,
          "marketplace chain order lookup failed",
          e);
    }
    LocalDateTime deadlineAt = order == null ? null : deadlineAt(order.deadlineEpochSeconds());
    if (order == null || order.isAbsent()) {
      return PhaseBChainInspection.manualSync(
          MarketplaceAdminReviewValidationCode.CHAIN_ORDER_ABSENT,
          "marketplace chain order is absent before admin execution",
          order,
          deadlineAt);
    }
    if (order.state() == ReservationEscrowOrderView.STATE_CREATED) {
      if (deadlineAt != null && !deadlineAt.isAfter(LocalDateTime.now(clock))) {
        return PhaseBChainInspection.deadlineSync(
            MarketplaceAdminReviewValidationCode.CONTRACT_DEADLINE_EXPIRED,
            "marketplace chain order deadline expired before admin execution",
            order,
            deadlineAt);
      }
      return PhaseBChainInspection.proceed(order);
    }
    return switch (order.state()) {
      case ReservationEscrowOrderView.STATE_ADMIN_REFUNDED -> phaseBRefundedOutcome(phaseA, order);
      case ReservationEscrowOrderView.STATE_ADMIN_SETTLED,
              ReservationEscrowOrderView.STATE_CONFIRMED ->
          phaseBSettledOutcome(phaseA, order);
      case ReservationEscrowOrderView.STATE_DEADLINE_REFUNDED ->
          PhaseBChainInspection.terminal(
              MarketplaceAdminReviewValidationCode.CHAIN_MISMATCH_REQUIRES_SYNC,
              "marketplace chain order was already deadline-refunded before admin execution",
              order,
              ReservationStatus.DEADLINE_REFUNDED,
              ReservationEscrowStatus.DEADLINE_REFUNDED,
              deadlineAt);
      case ReservationEscrowOrderView.STATE_CANCELLED ->
          PhaseBChainInspection.manualSync(
              MarketplaceAdminReviewValidationCode.CHAIN_MISMATCH_REQUIRES_SYNC,
              "marketplace chain order was already cancelled before admin execution",
              order,
              deadlineAt);
      default ->
          PhaseBChainInspection.manualSync(
              MarketplaceAdminReviewValidationCode.CHAIN_ORDER_NOT_CREATED,
              "marketplace chain order is not in CREATED state before admin execution",
              order,
              deadlineAt);
    };
  }

  private PhaseBChainInspection phaseBRefundedOutcome(
      AdminPhaseA phaseA, ReservationEscrowOrderView order) {
    if (phaseA.action() != ReservationEscrowAction.ADMIN_REFUND) {
      return PhaseBChainInspection.manualSync(
          MarketplaceAdminReviewValidationCode.CHAIN_ORDER_ALREADY_REFUNDED,
          "marketplace chain order was already refunded before admin settlement",
          order,
          deadlineAt(order.deadlineEpochSeconds()));
    }
    return PhaseBChainInspection.terminal(
        MarketplaceAdminReviewValidationCode.CHAIN_ORDER_ALREADY_REFUNDED,
        "marketplace chain order was already admin-refunded before admin execution",
        order,
        ReservationStatus.TIMEOUT_CANCELLED,
        ReservationEscrowStatus.REFUNDED,
        deadlineAt(order.deadlineEpochSeconds()));
  }

  private PhaseBChainInspection phaseBSettledOutcome(
      AdminPhaseA phaseA, ReservationEscrowOrderView order) {
    if (phaseA.action() != ReservationEscrowAction.ADMIN_SETTLE) {
      return PhaseBChainInspection.manualSync(
          MarketplaceAdminReviewValidationCode.CHAIN_ORDER_ALREADY_SETTLED,
          "marketplace chain order was already settled before admin refund",
          order,
          deadlineAt(order.deadlineEpochSeconds()));
    }
    ReservationStatus terminalStatus =
        order.state() == ReservationEscrowOrderView.STATE_CONFIRMED
            ? ReservationStatus.SETTLED
            : ReservationStatus.AUTO_SETTLED;
    return PhaseBChainInspection.terminal(
        MarketplaceAdminReviewValidationCode.CHAIN_ORDER_ALREADY_SETTLED,
        "marketplace chain order was already settled before admin execution",
        order,
        terminalStatus,
        ReservationEscrowStatus.SETTLED,
        deadlineAt(order.deadlineEpochSeconds()));
  }

  private void validateBaseExecutable(
      ReservationEscrowAction action,
      Reservation reservation,
      MarketplaceReservationActionState latestActionState) {
    ReservationStatus expected =
        action == ReservationEscrowAction.ADMIN_REFUND
            ? ReservationStatus.PENDING
            : ReservationStatus.APPROVED;
    if (reservation.getStatus() != expected
        || reservation.getEffectiveEscrowStatus() != ReservationEscrowStatus.LOCKED
        || reservation.getEffectiveEscrowFlow() != ReservationEscrowFlow.USER_EIP7702) {
      throw conflict(MarketplaceAdminReviewValidationCode.INVALID_LOCAL_STATUS);
    }
    if (reservation.getCurrentExecutionIntentPublicId() != null
        || (latestActionState != null
            && latestActionState.getStatus() != null
            && latestActionState.getStatus().isActive())) {
      throw conflict(MarketplaceAdminReviewValidationCode.ACTIVE_EXECUTION_EXISTS);
    }
    if (executionCandidateGuard != null
        && executionCandidateGuard.hasBlockingExecutionForAnyMarketplaceAction(reservation)) {
      throw conflict(MarketplaceAdminReviewValidationCode.ACTIVE_EXECUTION_EXISTS);
    }
  }

  private void validateManualRefundReason(
      MarketplaceAdminRefundReasonCode reasonCode, boolean canManualRefund) {
    if (reasonCode == null) {
      throw conflict(MarketplaceAdminReviewValidationCode.INVALID_REASON_CODE);
    }
    if (reasonCode == MarketplaceAdminRefundReasonCode.ADMIN_MANUAL_REFUND && !canManualRefund) {
      throw conflict(MarketplaceAdminReviewValidationCode.ELEVATED_AUTHORITY_REQUIRED);
    }
  }

  private void validateManualSettlementReason(
      MarketplaceAdminSettleReasonCode reasonCode, boolean canEarlySettle) {
    if (reasonCode == null) {
      throw conflict(MarketplaceAdminReviewValidationCode.INVALID_REASON_CODE);
    }
    if (reasonCode == MarketplaceAdminSettleReasonCode.ADMIN_MANUAL_SETTLE && !canEarlySettle) {
      throw conflict(MarketplaceAdminReviewValidationCode.ELEVATED_AUTHORITY_REQUIRED);
    }
  }

  private void validateSchedulerRefundReason(MarketplaceAdminRefundReasonCode reasonCode) {
    if (reasonCode == null) {
      throw conflict(MarketplaceAdminReviewValidationCode.INVALID_REASON_CODE);
    }
    if (reasonCode == MarketplaceAdminRefundReasonCode.ADMIN_MANUAL_REFUND) {
      throw conflict(MarketplaceAdminReviewValidationCode.INVALID_REASON_CODE);
    }
  }

  private void validateSchedulerSettlementReason(MarketplaceAdminSettleReasonCode reasonCode) {
    if (reasonCode == null) {
      throw conflict(MarketplaceAdminReviewValidationCode.INVALID_REASON_CODE);
    }
    if (reasonCode == MarketplaceAdminSettleReasonCode.ADMIN_MANUAL_SETTLE) {
      throw conflict(MarketplaceAdminReviewValidationCode.INVALID_REASON_CODE);
    }
  }

  private void validateReasonWindow(
      ReservationEscrowAction action, String reasonCode, Reservation reservation) {
    LocalDateTime now = LocalDateTime.now(clock);
    if (action == ReservationEscrowAction.ADMIN_REFUND) {
      MarketplaceAdminRefundReasonCode reason =
          MarketplaceAdminRefundReasonCode.valueOf(reasonCode);
      if (reason == MarketplaceAdminRefundReasonCode.TRAINER_TIMEOUT
          && (reservation.getCreatedAt() == null
              || reservation.getCreatedAt().plusHours(72).isAfter(now))) {
        throw conflict(MarketplaceAdminReviewValidationCode.APPROVAL_TIMEOUT_NOT_REACHED);
      }
      if (reason == MarketplaceAdminRefundReasonCode.SESSION_START_WINDOW_TIMEOUT
          && reservation
              .sessionEndAt()
              .minusMinutes(reservation.getDurationMinutes())
              .minusHours(1)
              .isAfter(now)) {
        throw conflict(MarketplaceAdminReviewValidationCode.SESSION_START_WINDOW_NOT_REACHED);
      }
      return;
    }
    MarketplaceAdminSettleReasonCode reason = MarketplaceAdminSettleReasonCode.valueOf(reasonCode);
    if (reason == MarketplaceAdminSettleReasonCode.BUYER_CONFIRMATION_TIMEOUT
        && reservation.sessionEndAt().plusHours(24).isAfter(now)) {
      throw conflict(MarketplaceAdminReviewValidationCode.CLASS_NOT_ENDED);
    }
    if (reason == MarketplaceAdminSettleReasonCode.ADMIN_MANUAL_SETTLE
        && reservation.sessionEndAt().isAfter(now)) {
      throw conflict(MarketplaceAdminReviewValidationCode.CLASS_NOT_ENDED);
    }
  }

  private void validatePreparedSnapshot(
      AdminPhaseA phaseA,
      MarketplaceReservationActionState actionState,
      Reservation reservation,
      MarketplaceReservationEscrow escrow) {
    boolean matches =
        actionState.getStatus() == ReservationActionStateStatus.PREPARING
            && phaseA.actionState().getAttemptToken().equals(actionState.getAttemptToken())
            && actionState.getExecutionIntentPublicId() == null
            && Objects.equals(actionState.getExpectedReservationVersion(), reservation.getVersion())
            && Objects.equals(
                actionState.getExpectedReservationVersion(), phaseA.reservation().getVersion())
            && reservation.getStatus() == phaseA.reservation().getStatus()
            && reservation.getEffectiveEscrowStatus()
                == phaseA.reservation().getEffectiveEscrowStatus()
            && escrow.getEscrowStatus() == phaseA.escrow().getEscrowStatus();
    if (!matches) {
      throw new AdminPhaseCStaleException(
          MarketplaceAdminReviewValidationCode.PREPARED_SNAPSHOT_MISMATCH.name(),
          "admin prepared snapshot changed before bind");
    }
  }

  private PrepareMarketplaceAdminEscrowCommand prepareCommand(
      ReservationEscrowAction action,
      ReservationActionRequestSource source,
      Long operatorUserId,
      String schedulerRunId,
      String reasonCode,
      String memo,
      String rootKey,
      Reservation reservation,
      MarketplaceReservationEscrow escrow,
      MarketplaceReservationActionState actionState) {
    return new PrepareMarketplaceAdminEscrowCommand(
        action,
        reservation.getId(),
        reservation.getOrderId(),
        reservation.getOrderKey(),
        reservation.getUserId(),
        reservation.getTrainerId(),
        reservation.getUserId(),
        reservation.getTrainerId(),
        reservation.getVersion(),
        reservation.getStatus(),
        reservation.getEffectiveEscrowStatus(),
        reservation.getBuyerWalletAddress(),
        reservation.getTrainerWalletAddress(),
        reservation.getTokenAddress(),
        new BigInteger(reservation.getPriceBaseUnits()),
        reservation.getBookedPriceAmount(),
        reservation.sessionEndAt(),
        actionState.getAttemptToken(),
        action == ReservationEscrowAction.ADMIN_REFUND
            ? ReservationStatus.TIMEOUT_CANCELLED.name()
            : ReservationStatus.AUTO_SETTLED.name(),
        escrow.getId(),
        actionState.getId(),
        source,
        operatorUserId,
        schedulerRunId,
        reasonCode,
        memo,
        rootKey);
  }

  private static String rootIdempotencyKey(
      ReservationEscrowAction action,
      Long reservationId,
      ReservationActionRequestSource source,
      String reasonCode) {
    return "marketplace-admin:"
        + marketplaceActionType(action).toLowerCase(Locale.ROOT)
        + ":"
        + reservationId
        + ":"
        + source.name().toLowerCase(Locale.ROOT)
        + ":"
        + reasonCode;
  }

  private static String marketplaceActionType(ReservationEscrowAction action) {
    return switch (action) {
      case ADMIN_REFUND -> "MARKETPLACE_ADMIN_REFUND";
      case ADMIN_SETTLE -> "MARKETPLACE_ADMIN_SETTLE";
      default ->
          throw new IllegalArgumentException("unsupported marketplace admin action: " + action);
    };
  }

  private static MarketplaceReservationStateException conflict(
      MarketplaceAdminReviewValidationCode code) {
    return new MarketplaceReservationStateException(
        ErrorCode.MARKETPLACE_RESERVATION_INVALID_STATUS, code.name());
  }

  private static String errorCode(RuntimeException cause) {
    if (cause instanceof AdminPhaseCStaleException staleException) {
      return staleException.code();
    }
    if (cause instanceof AdminPhaseBTransientException transientException) {
      return transientException.code().name();
    }
    if (cause instanceof AdminPhaseBChainMismatchException mismatchException) {
      return mismatchException.inspection().code().name();
    }
    MarketplaceAdminReviewValidationCode adminCode = validationCode(cause);
    if (adminCode != null) {
      return adminCode.name();
    }
    return cause instanceof BusinessException businessException
        ? businessException.getCode()
        : cause.getClass().getSimpleName();
  }

  private static ReservationActionStateStatus phaseCFailureStatus(RuntimeException cause) {
    MarketplaceAdminReviewValidationCode code = validationCode(cause);
    if (code == MarketplaceAdminReviewValidationCode.IDEMPOTENCY_CONFLICT
        || code == MarketplaceAdminReviewValidationCode.IDEMPOTENCY_REUSE_ATTEMPT_MISMATCH) {
      return ReservationActionStateStatus.STALE;
    }
    return ReservationActionStateStatus.PREPARATION_FAILED;
  }

  private static MarketplaceAdminReviewValidationCode validationCode(RuntimeException cause) {
    if (cause == null) {
      return null;
    }
    MarketplaceAdminReviewValidationCode messageCode = parseValidationCode(cause.getMessage());
    if (messageCode != null) {
      return messageCode;
    }
    if (cause instanceof BusinessException businessException) {
      String code = businessException.getCode();
      if (ErrorCode.IDEMPOTENCY_CONFLICT.getCode().equals(code)
          || ErrorCode.MARKETPLACE_IDEMPOTENCY_CONFLICT.getCode().equals(code)) {
        return MarketplaceAdminReviewValidationCode.IDEMPOTENCY_CONFLICT;
      }
      if (ErrorCode.MARKETPLACE_ACTIVE_EXECUTION_CONFLICT.getCode().equals(code)) {
        return MarketplaceAdminReviewValidationCode.INTENT_BIND_CONFLICT;
      }
      if (ErrorCode.MARKETPLACE_RESERVATION_INVALID_STATUS.getCode().equals(code)) {
        return parseValidationCode(cause.getMessage());
      }
    }
    return cause.getCause() instanceof RuntimeException nested ? validationCode(nested) : null;
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

  private LocalDateTime deadlineAt(Long epochSeconds) {
    if (epochSeconds == null) {
      return null;
    }
    return LocalDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), clock.getZone());
  }

  private static final class AdminPhaseBTransientException extends RuntimeException {

    private final MarketplaceAdminReviewValidationCode code;

    private AdminPhaseBTransientException(
        MarketplaceAdminReviewValidationCode code, String message, RuntimeException cause) {
      super(message, cause);
      this.code = code;
    }

    private MarketplaceAdminReviewValidationCode code() {
      return code;
    }
  }

  private static final class AdminPhaseBChainMismatchException
      extends MarketplaceReservationStateException {

    private final PhaseBChainInspection inspection;

    private AdminPhaseBChainMismatchException(PhaseBChainInspection inspection) {
      super(ErrorCode.MARKETPLACE_RESERVATION_INVALID_STATUS, inspection.message());
      this.inspection = inspection;
    }

    private PhaseBChainInspection inspection() {
      return inspection;
    }
  }

  private record PhaseBChainInspection(
      boolean proceed,
      MarketplaceAdminReviewValidationCode code,
      String message,
      ReservationEscrowOrderView order,
      ReservationStatus outcomeStatus,
      ReservationEscrowStatus outcomeEscrowStatus,
      boolean deadlineSync,
      LocalDateTime deadlineAt) {

    private static PhaseBChainInspection proceed(ReservationEscrowOrderView order) {
      return new PhaseBChainInspection(
          true, MarketplaceAdminReviewValidationCode.OK, null, order, null, null, false, null);
    }

    private static PhaseBChainInspection deadlineSync(
        MarketplaceAdminReviewValidationCode code,
        String message,
        ReservationEscrowOrderView order,
        LocalDateTime deadlineAt) {
      return new PhaseBChainInspection(false, code, message, order, null, null, true, deadlineAt);
    }

    private static PhaseBChainInspection manualSync(
        MarketplaceAdminReviewValidationCode code,
        String message,
        ReservationEscrowOrderView order,
        LocalDateTime deadlineAt) {
      return new PhaseBChainInspection(
          false,
          code,
          message,
          order,
          ReservationStatus.MANUAL_SYNC_REQUIRED,
          ReservationEscrowStatus.MANUAL_SYNC_REQUIRED,
          false,
          deadlineAt);
    }

    private static PhaseBChainInspection terminal(
        MarketplaceAdminReviewValidationCode code,
        String message,
        ReservationEscrowOrderView order,
        ReservationStatus outcomeStatus,
        ReservationEscrowStatus outcomeEscrowStatus,
        LocalDateTime deadlineAt) {
      return new PhaseBChainInspection(
          false, code, message, order, outcomeStatus, outcomeEscrowStatus, false, deadlineAt);
    }

    private Reservation applyTo(Reservation reservation) {
      if (deadlineSync) {
        return reservation.markDeadlineSyncRequired(code.name(), message);
      }
      Long deadlineEpochSeconds = order == null ? null : order.deadlineEpochSeconds();
      return reservation.syncChainOutcome(
          outcomeStatus,
          outcomeEscrowStatus,
          null,
          deadlineEpochSeconds,
          deadlineAt,
          ReservationTerminalResolvedBy.CHAIN_SYNC,
          code.name());
    }
  }

  private static final class AdminPhaseCStaleException
      extends MarketplaceReservationStateException {

    private final String code;

    private AdminPhaseCStaleException(String code, String message) {
      super(ErrorCode.MARKETPLACE_RESERVATION_INVALID_STATUS, message);
      this.code = code;
    }

    private String code() {
      return code;
    }
  }

  private record AdminPhaseA(
      ReservationEscrowAction action,
      Reservation reservation,
      MarketplaceReservationEscrow escrow,
      MarketplaceReservationActionState actionState,
      PrepareMarketplaceAdminEscrowCommand prepareCommand) {}
}
