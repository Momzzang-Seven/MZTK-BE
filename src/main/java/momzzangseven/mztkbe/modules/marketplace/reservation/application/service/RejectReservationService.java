package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import java.time.Clock;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.ErrorCode;
import momzzangseven.mztkbe.global.error.marketplace.MarketplaceReservationStateException;
import momzzangseven.mztkbe.global.error.marketplace.MarketplaceUnauthorizedAccessException;
import momzzangseven.mztkbe.global.error.marketplace.ReservationNotFoundException;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.PrepareReservationEscrowCommand;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.PrepareReservationEscrowResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.RejectReservationCommand;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.RejectReservationResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.RejectReservationUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.BindReservationActionStatePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.CancelReservationEscrowExecutionPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationActionStatePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationEscrowPaymentConfigPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationEscrowPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationExecutionCandidatePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationExecutionStatePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationWalletPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.PrepareReservationEscrowExecutionPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.RunReservationTransactionPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SaveReservationActionStatePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SaveReservationEscrowPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SaveReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.MarketplaceReservationActionState;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.MarketplaceReservationEscrow;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.Reservation;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationActionStateStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowAction;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowActorType;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;

/**
 * Service for a trainer to reject a PENDING reservation.
 *
 * <p><b>Two-phase ordering:</b><br>
 * The reservation first moves to a scheduler-invisible reject-pending state, then Web3 prepare runs
 * outside the row lock. Phase B relocks and binds the shared execution intent before returning the
 * sign request. REJECTED and the trainer strike are applied only by the confirmed execution hook.
 */
@Slf4j
public class RejectReservationService implements RejectReservationUseCase {

  private final LoadReservationPort loadReservationPort;
  private final SaveReservationPort saveReservationPort;
  private final PrepareReservationEscrowExecutionPort prepareReservationEscrowExecutionPort;
  private final CancelReservationEscrowExecutionPort cancelReservationEscrowExecutionPort;
  private final LoadReservationWalletPort loadReservationWalletPort;
  private final LoadReservationEscrowPaymentConfigPort loadReservationEscrowPaymentConfigPort;
  private final LoadReservationEscrowPort loadReservationEscrowPort;
  private final SaveReservationEscrowPort saveReservationEscrowPort;
  private final SaveReservationActionStatePort saveReservationActionStatePort;
  private final LoadReservationActionStatePort loadReservationActionStatePort;
  private final BindReservationActionStatePort bindReservationActionStatePort;
  private final ReservationUnresolvedExecutionGuard unresolvedExecutionGuard;
  private final Clock clock;
  private RunReservationTransactionPort transactionPort;

  public RejectReservationService(
      LoadReservationPort loadReservationPort,
      SaveReservationPort saveReservationPort,
      PrepareReservationEscrowExecutionPort prepareReservationEscrowExecutionPort,
      CancelReservationEscrowExecutionPort cancelReservationEscrowExecutionPort,
      LoadReservationWalletPort loadReservationWalletPort,
      LoadReservationEscrowPaymentConfigPort loadReservationEscrowPaymentConfigPort,
      Clock clock) {
    this(
        loadReservationPort,
        saveReservationPort,
        prepareReservationEscrowExecutionPort,
        cancelReservationEscrowExecutionPort,
        loadReservationWalletPort,
        loadReservationEscrowPaymentConfigPort,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        clock);
  }

  public RejectReservationService(
      LoadReservationPort loadReservationPort,
      SaveReservationPort saveReservationPort,
      PrepareReservationEscrowExecutionPort prepareReservationEscrowExecutionPort,
      CancelReservationEscrowExecutionPort cancelReservationEscrowExecutionPort,
      LoadReservationWalletPort loadReservationWalletPort,
      LoadReservationEscrowPaymentConfigPort loadReservationEscrowPaymentConfigPort,
      LoadReservationEscrowPort loadReservationEscrowPort,
      SaveReservationEscrowPort saveReservationEscrowPort,
      SaveReservationActionStatePort saveReservationActionStatePort,
      LoadReservationActionStatePort loadReservationActionStatePort,
      BindReservationActionStatePort bindReservationActionStatePort,
      LoadReservationExecutionStatePort loadReservationExecutionStatePort,
      LoadReservationExecutionCandidatePort loadReservationExecutionCandidatePort,
      Clock clock) {
    this.loadReservationPort = loadReservationPort;
    this.saveReservationPort = saveReservationPort;
    this.prepareReservationEscrowExecutionPort =
        java.util.Objects.requireNonNull(prepareReservationEscrowExecutionPort);
    this.cancelReservationEscrowExecutionPort =
        java.util.Objects.requireNonNull(cancelReservationEscrowExecutionPort);
    this.loadReservationWalletPort = java.util.Objects.requireNonNull(loadReservationWalletPort);
    this.loadReservationEscrowPaymentConfigPort =
        java.util.Objects.requireNonNull(loadReservationEscrowPaymentConfigPort);
    this.loadReservationEscrowPort = loadReservationEscrowPort;
    this.saveReservationEscrowPort = saveReservationEscrowPort;
    this.saveReservationActionStatePort = saveReservationActionStatePort;
    this.loadReservationActionStatePort = loadReservationActionStatePort;
    this.bindReservationActionStatePort = bindReservationActionStatePort;
    this.unresolvedExecutionGuard =
        new ReservationUnresolvedExecutionGuard(
            loadReservationActionStatePort,
            loadReservationExecutionStatePort == null
                    || loadReservationExecutionCandidatePort == null
                ? null
                : new ReservationExecutionCandidateGuard(
                    loadReservationExecutionStatePort, loadReservationExecutionCandidatePort));
    this.clock = clock;
  }

  public RejectReservationService(
      LoadReservationPort loadReservationPort,
      SaveReservationPort saveReservationPort,
      PrepareReservationEscrowExecutionPort prepareReservationEscrowExecutionPort,
      CancelReservationEscrowExecutionPort cancelReservationEscrowExecutionPort,
      LoadReservationWalletPort loadReservationWalletPort,
      LoadReservationEscrowPaymentConfigPort loadReservationEscrowPaymentConfigPort,
      LoadReservationEscrowPort loadReservationEscrowPort,
      SaveReservationActionStatePort saveReservationActionStatePort,
      LoadReservationActionStatePort loadReservationActionStatePort,
      BindReservationActionStatePort bindReservationActionStatePort,
      LoadReservationExecutionStatePort loadReservationExecutionStatePort,
      LoadReservationExecutionCandidatePort loadReservationExecutionCandidatePort,
      Clock clock) {
    this(
        loadReservationPort,
        saveReservationPort,
        prepareReservationEscrowExecutionPort,
        cancelReservationEscrowExecutionPort,
        loadReservationWalletPort,
        loadReservationEscrowPaymentConfigPort,
        loadReservationEscrowPort,
        null,
        saveReservationActionStatePort,
        loadReservationActionStatePort,
        bindReservationActionStatePort,
        loadReservationExecutionStatePort,
        loadReservationExecutionCandidatePort,
        clock);
  }

  public void setTransactionPort(RunReservationTransactionPort transactionPort) {
    this.transactionPort = java.util.Objects.requireNonNull(transactionPort);
  }

  @Override
  public RejectReservationResult execute(RejectReservationCommand command) {
    log.debug(
        "RejectReservation: reservationId={}, trainerId={}",
        command.reservationId(),
        command.authenticatedTrainerId());

    PendingPreparation phaseA = runInTransaction(() -> beginReject(command));
    if (phaseA.deadlineRefundRequired()) {
      throw deadlineRefundRequired();
    }
    PrepareReservationEscrowResult prepared;
    try {
      prepared = prepareReservationEscrowExecutionPort.prepareCancel(requirePrepareCommand(phaseA));
    } catch (RuntimeException e) {
      rollbackPending(phaseA);
      throw e;
    }
    Reservation saved;
    try {
      saved =
          runInTransaction(
              () ->
                  bindPendingExecution(
                      phaseA,
                      prepared.web3().executionIntent().id(),
                      ReservationStatus.REJECT_PENDING));
    } catch (RuntimeException e) {
      compensateBindFailure(phaseA, prepared, e);
      throw e;
    }

    log.info(
        "Reservation reject intent prepared: id={}, trainerId={}, intentId={}",
        saved.getId(),
        command.authenticatedTrainerId(),
        prepared.web3().executionIntent().id());
    return new RejectReservationResult(
        saved.getId(),
        ReservationDisplayStatusMapper.displayStatus(saved),
        ReservationDisplayStatusMapper.businessStatus(saved),
        saved.getEffectiveEscrowStatus().name(),
        prepared.web3());
  }

  private PendingPreparation beginReject(RejectReservationCommand command) {
    Reservation reservation =
        loadReservationPort
            .findByIdWithLock(command.reservationId())
            .orElseThrow(() -> new ReservationNotFoundException(command.reservationId()));

    if (!reservation.isOwnedByTrainer(command.authenticatedTrainerId())) {
      throw new MarketplaceUnauthorizedAccessException();
    }

    if (!reservation.getStatus().canTransitionTo(ReservationStatus.REJECTED)) {
      throw new MarketplaceReservationStateException(
          ErrorCode.MARKETPLACE_RESERVATION_INVALID_STATUS,
          "Cannot reject reservation in status: " + reservation.getStatus());
    }
    ReservationEscrowActionGuard.requireUserEscrowLocked(reservation, "reject");
    MarketplaceReservationEscrow escrow = loadEscrowProjection(reservation);
    if (escrow != null) {
      ReservationEscrowActionGuard.requireActiveWalletMatchesSnapshot(
          loadReservationWalletPort,
          command.authenticatedTrainerId(),
          escrow.getTrainerWalletAddress());
    }
    Reservation deadlineRefundRequired = routeExpiredToDeadlineRefund(reservation);
    if (deadlineRefundRequired != null) {
      return new PendingPreparation(deadlineRefundRequired, null, null, true);
    }
    unresolvedExecutionGuard.requireNoUnresolvedExecution(reservation, "reject");

    Reservation pending =
        saveReservationPort.save(
            reservation.beginRejectPending(
                UUID.randomUUID().toString(), command.rejectionReason()));
    MarketplaceReservationActionState actionState =
        createActionState(
            pending,
            escrow,
            ReservationEscrowAction.TRAINER_REJECT,
            ReservationEscrowActorType.TRAINER,
            command.authenticatedTrainerId(),
            ReservationStatus.PENDING,
            ReservationEscrowStatus.LOCKED,
            command.rejectionReason());
    return new PendingPreparation(
        pending,
        commandFor(
            pending,
            escrow,
            actionState,
            "TRAINER",
            command.authenticatedTrainerId(),
            pending.getUserId(),
            "REJECTED"),
        actionState,
        false);
  }

  private Reservation bindPendingExecution(
      PendingPreparation phaseA, String executionIntentId, ReservationStatus expectedStatus) {
    Reservation current =
        loadReservationPort
            .findByIdWithLock(phaseA.reservation().getId())
            .orElseThrow(() -> new ReservationNotFoundException(phaseA.reservation().getId()));
    validatePendingSnapshot(current, phaseA.reservation(), expectedStatus);
    bindActionState(phaseA, executionIntentId);
    return saveReservationPort.save(current.bindPendingExecutionIntent(executionIntentId));
  }

  private void rollbackPending(PendingPreparation phaseA) {
    runInTransaction(
        () -> {
          loadReservationPort
              .findByIdWithLock(phaseA.reservation().getId())
              .filter(
                  reservation ->
                      reservation.getStatus() == phaseA.reservation().getStatus()
                          && reservation.getCurrentExecutionIntentPublicId() == null
                          && equalsNullable(
                              reservation.getPendingAttemptToken(),
                              phaseA.reservation().getPendingAttemptToken()))
              .ifPresent(
                  reservation -> saveReservationPort.save(reservation.rollbackToPriorState()));
          markActionStatePreparationFailed(phaseA, "ROLLBACK", "marketplace action rolled back");
          return null;
        });
  }

  private void compensateBindFailure(
      PendingPreparation phaseA, PrepareReservationEscrowResult prepared, RuntimeException cause) {
    if (cancelSignablePreparedIntent(prepared, cause)) {
      rollbackPending(phaseA);
    }
  }

  private boolean cancelSignablePreparedIntent(
      PrepareReservationEscrowResult prepared, RuntimeException cause) {
    String executionIntentId = prepared.web3().executionIntent().id();
    try {
      return cancelReservationEscrowExecutionPort.cancelSignableIntent(
          executionIntentId,
          "MARKETPLACE_PHASE_B_BIND_FAILED",
          cause.getMessage() == null ? "marketplace reservation bind failed" : cause.getMessage());
    } catch (RuntimeException cancelFailure) {
      log.warn(
          "Failed to cancel marketplace reject intent after Phase B bind failure: intentId={}",
          executionIntentId,
          cancelFailure);
      return false;
    }
  }

  private void validatePendingSnapshot(
      Reservation current, Reservation expected, ReservationStatus expectedStatus) {
    if (current.getStatus() != expectedStatus
        || !equalsNullable(current.getPendingAttemptToken(), expected.getPendingAttemptToken())) {
      throw new MarketplaceReservationStateException(
          ErrorCode.MARKETPLACE_ACTIVE_EXECUTION_CONFLICT,
          "marketplace reservation state changed before execution intent bind");
    }
  }

  private Reservation routeExpiredToDeadlineRefund(Reservation reservation) {
    if (!ReservationEscrowActionGuard.isAfterContractDeadline(reservation, clock)) {
      return null;
    }
    Reservation marked = saveReservationPort.save(reservation.markDeadlineRefundAvailable());
    syncDeadlineRefundAvailableProjection(marked);
    return marked;
  }

  private void syncDeadlineRefundAvailableProjection(Reservation reservation) {
    if (loadReservationEscrowPort == null || saveReservationEscrowPort == null) {
      return;
    }
    loadReservationEscrowPort
        .findByReservationIdWithLock(reservation.getId())
        .map(
            escrow ->
                escrow.toBuilder()
                    .escrowStatus(ReservationEscrowStatus.DEADLINE_REFUND_AVAILABLE)
                    .build())
        .ifPresent(saveReservationEscrowPort::save);
  }

  private MarketplaceReservationStateException deadlineRefundRequired() {
    return new MarketplaceReservationStateException(
        ErrorCode.MARKETPLACE_DEADLINE_REFUND_REQUIRED,
        "Reservation deadline expired; use the deadline refund flow");
  }

  private PrepareReservationEscrowCommand requirePrepareCommand(PendingPreparation phaseA) {
    if (phaseA.prepareCommand() == null) {
      throw deadlineRefundRequired();
    }
    return phaseA.prepareCommand();
  }

  private boolean equalsNullable(Object left, Object right) {
    return left == null ? right == null : left.equals(right);
  }

  private <T> T runInTransaction(java.util.function.Supplier<T> supplier) {
    return transactionPort.requiresNew(supplier);
  }

  private PrepareReservationEscrowCommand commandFor(
      Reservation reservation,
      MarketplaceReservationEscrow escrow,
      MarketplaceReservationActionState actionState,
      String actorType,
      Long authorityUserId,
      Long counterpartyUserId,
      String targetTerminalStatus) {
    String buyerWallet =
        ReservationEscrowActionGuard.walletOrSnapshot(
            loadReservationWalletPort,
            escrow == null ? reservation.getBuyerWalletAddress() : escrow.getBuyerWalletAddress(),
            reservation.getUserId());
    String trainerWallet =
        ReservationEscrowActionGuard.walletOrSnapshot(
            loadReservationWalletPort,
            escrow == null
                ? reservation.getTrainerWalletAddress()
                : escrow.getTrainerWalletAddress(),
            reservation.getTrainerId());
    var payment = loadReservationEscrowPaymentConfigPort.load();
    return new PrepareReservationEscrowCommand(
        reservation.getId(),
        reservation.getOrderId(),
        escrow == null
            ? ReservationOrderKeySupport.requireOrderKey(reservation)
            : escrow.getOrderKey(),
        actorType,
        authorityUserId,
        authorityUserId,
        counterpartyUserId,
        reservation.getUserId(),
        reservation.getTrainerId(),
        reservation.getVersion(),
        reservation.getPriorStatus(),
        reservation.getPriorEscrowStatus(),
        buyerWallet,
        trainerWallet,
        escrow == null || escrow.getTokenAddress() == null
            ? reservation.getTokenAddress() == null
                ? payment.tokenAddress()
                : reservation.getTokenAddress()
            : escrow.getTokenAddress(),
        escrow == null || escrow.getPriceBaseUnits() == null
            ? reservation.getPriceBaseUnits() == null
                ? payment.priceBaseUnits(reservation.getBookedPriceAmount()).toString()
                : reservation.getPriceBaseUnits()
            : escrow.getPriceBaseUnits().toString(),
        reservation.getBookedPriceAmount(),
        reservation.sessionEndAt(),
        escrow == null
            ? reservation.getExpectedContractDeadlineEpochSeconds()
            : escrow.getExpectedContractDeadlineEpochSeconds(),
        escrow == null
            ? reservation.getContractDeadlineEpochSeconds()
            : escrow.getContractDeadlineEpochSeconds(),
        reservation.getPendingAttemptToken(),
        targetTerminalStatus,
        escrow == null ? null : escrow.getId(),
        actionState == null ? null : actionState.getId(),
        actionState == null ? null : actionState.getRootIdempotencyKey());
  }

  private MarketplaceReservationEscrow loadEscrowProjection(Reservation reservation) {
    if (loadReservationEscrowPort == null) {
      return null;
    }
    MarketplaceReservationEscrow escrow =
        loadReservationEscrowPort
            .findByReservationIdWithLock(reservation.getId())
            .orElseThrow(
                () ->
                    new MarketplaceReservationStateException(
                        ErrorCode.MARKETPLACE_DEADLINE_SYNC_REQUIRED,
                        "Reservation escrow projection is missing"));
    if (!escrow.getEscrowFlow().isUserEip7702()
        || escrow.getEscrowStatus() != ReservationEscrowStatus.LOCKED) {
      throw new MarketplaceReservationStateException(
          ErrorCode.MARKETPLACE_RESERVATION_INVALID_STATUS,
          "Reservation escrow projection is not locked");
    }
    return escrow;
  }

  private MarketplaceReservationActionState createActionState(
      Reservation reservation,
      MarketplaceReservationEscrow escrow,
      ReservationEscrowAction action,
      ReservationEscrowActorType actorType,
      Long actorUserId,
      ReservationStatus expectedStatus,
      ReservationEscrowStatus expectedEscrowStatus,
      String actionReason) {
    if (saveReservationActionStatePort == null) {
      return null;
    }
    int attemptNo =
        loadReservationActionStatePort == null
            ? 1
            : loadReservationActionStatePort
                    .findLatestByReservationId(reservation.getId())
                    .map(MarketplaceReservationActionState::getAttemptNo)
                    .orElse(0)
                + 1;
    MarketplaceReservationActionState saved =
        saveReservationActionStatePort.save(
            MarketplaceReservationActionState.builder()
                .reservationId(reservation.getId())
                .escrowId(escrow == null ? null : escrow.getId())
                .actionType(action)
                .actorType(actorType)
                .actorUserId(actorUserId)
                .attemptNo(attemptNo)
                .attemptToken(reservation.getPendingAttemptToken())
                .status(ReservationActionStateStatus.PREPARING)
                .expectedReservationVersion(reservation.getVersion())
                .expectedReservationStatus(expectedStatus)
                .expectedEscrowStatus(expectedEscrowStatus)
                .priorReservationStatus(reservation.getPriorStatus())
                .priorEscrowStatus(reservation.getPriorEscrowStatus())
                .actionReason(actionReason)
                .build());
    return saveReservationActionStatePort.save(
        saved.toBuilder()
            .rootIdempotencyKey(rootIdempotencyKey(escrow, action, saved.getId()))
            .build());
  }

  private String rootIdempotencyKey(
      MarketplaceReservationEscrow escrow, ReservationEscrowAction action, Long actionStateId) {
    String orderKey = escrow == null ? "reservation" : escrow.getOrderKey();
    return "order:" + orderKey + ":escrow-action:" + action.name() + ":state:" + actionStateId;
  }

  private void bindActionState(PendingPreparation phaseA, String executionIntentId) {
    if (phaseA.actionState() == null || bindReservationActionStatePort == null) {
      return;
    }
    bindReservationActionStatePort
        .bindExecutionIntent(
            phaseA.actionState().getId(), phaseA.actionState().getAttemptToken(), executionIntentId)
        .orElseThrow(
            () ->
                new MarketplaceReservationStateException(
                    ErrorCode.MARKETPLACE_ACTIVE_EXECUTION_CONFLICT,
                    "marketplace reservation action state changed before execution intent bind"));
  }

  private void markActionStatePreparationFailed(
      PendingPreparation phaseA, String errorCode, String errorReason) {
    if (phaseA.actionState() == null || saveReservationActionStatePort == null) {
      return;
    }
    saveReservationActionStatePort.save(
        phaseA.actionState().toBuilder()
            .status(ReservationActionStateStatus.PREPARATION_FAILED)
            .retryable(false)
            .errorCode(errorCode)
            .errorReason(errorReason)
            .build());
  }

  private record PendingPreparation(
      Reservation reservation,
      PrepareReservationEscrowCommand prepareCommand,
      MarketplaceReservationActionState actionState,
      boolean deadlineRefundRequired) {}
}
