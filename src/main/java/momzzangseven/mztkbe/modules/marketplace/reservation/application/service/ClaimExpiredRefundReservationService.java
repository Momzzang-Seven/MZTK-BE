package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.ErrorCode;
import momzzangseven.mztkbe.global.error.marketplace.MarketplaceReservationStateException;
import momzzangseven.mztkbe.global.error.marketplace.MarketplaceUnauthorizedAccessException;
import momzzangseven.mztkbe.global.error.marketplace.MarketplaceWeb3DisabledException;
import momzzangseven.mztkbe.global.error.marketplace.ReservationNotFoundException;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ClaimExpiredRefundReservationCommand;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ClaimExpiredRefundReservationResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.PrepareReservationEscrowCommand;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.PrepareReservationEscrowResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationEscrowOrderView;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationExecutionStateView;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationExecutionWriteView;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.ClaimExpiredRefundReservationUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.BindReservationActionStatePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.CancelReservationEscrowExecutionPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationActionStatePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationEscrowOrderPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationEscrowPaymentConfigPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationEscrowPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationExecutionStatePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationExecutionWritePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationWalletPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.PrepareReservationEscrowExecutionPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.RecordTrainerStrikePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.ReplayConfirmedReservationExecutionPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.RunReservationTransactionPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SaveReservationActionStatePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SaveReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.MarketplaceReservationActionState;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.MarketplaceReservationEscrow;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.Reservation;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationActionStateStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowAction;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowActorType;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.TrainerStrikeEvent;

/** Prepares the buyer-owned claimExpiredRefund marketplace execution. */
@Slf4j
public class ClaimExpiredRefundReservationService implements ClaimExpiredRefundReservationUseCase {

  private static final Set<String> RETRYABLE_TERMINAL_STATUSES =
      Set.of("FAILED_ONCHAIN", "EXPIRED", "CANCELED", "NONCE_STALE");

  private final LoadReservationPort loadReservationPort;
  private final SaveReservationPort saveReservationPort;
  private final PrepareReservationEscrowExecutionPort prepareReservationEscrowExecutionPort;
  private final CancelReservationEscrowExecutionPort cancelReservationEscrowExecutionPort;
  private final LoadReservationWalletPort loadReservationWalletPort;
  private final LoadReservationEscrowPaymentConfigPort loadReservationEscrowPaymentConfigPort;
  private final LoadReservationExecutionWritePort loadReservationExecutionWritePort;
  private final LoadReservationExecutionStatePort loadReservationExecutionStatePort;
  private final ReplayConfirmedReservationExecutionPort replayConfirmedReservationExecutionPort;
  private final LoadReservationEscrowOrderPort loadReservationEscrowOrderPort;
  private final LoadReservationEscrowPort loadReservationEscrowPort;
  private final SaveReservationActionStatePort saveReservationActionStatePort;
  private final LoadReservationActionStatePort loadReservationActionStatePort;
  private final BindReservationActionStatePort bindReservationActionStatePort;
  private final RecordTrainerStrikePort recordTrainerStrikePort;
  private final Clock clock;
  private RunReservationTransactionPort transactionPort;

  public ClaimExpiredRefundReservationService(
      LoadReservationPort loadReservationPort,
      SaveReservationPort saveReservationPort,
      PrepareReservationEscrowExecutionPort prepareReservationEscrowExecutionPort,
      CancelReservationEscrowExecutionPort cancelReservationEscrowExecutionPort,
      LoadReservationWalletPort loadReservationWalletPort,
      LoadReservationEscrowPaymentConfigPort loadReservationEscrowPaymentConfigPort,
      LoadReservationExecutionWritePort loadReservationExecutionWritePort,
      LoadReservationExecutionStatePort loadReservationExecutionStatePort,
      ReplayConfirmedReservationExecutionPort replayConfirmedReservationExecutionPort,
      LoadReservationEscrowOrderPort loadReservationEscrowOrderPort,
      RecordTrainerStrikePort recordTrainerStrikePort,
      Clock clock) {
    this(
        loadReservationPort,
        saveReservationPort,
        prepareReservationEscrowExecutionPort,
        cancelReservationEscrowExecutionPort,
        loadReservationWalletPort,
        loadReservationEscrowPaymentConfigPort,
        loadReservationExecutionWritePort,
        loadReservationExecutionStatePort,
        replayConfirmedReservationExecutionPort,
        loadReservationEscrowOrderPort,
        null,
        null,
        null,
        null,
        recordTrainerStrikePort,
        clock);
  }

  public ClaimExpiredRefundReservationService(
      LoadReservationPort loadReservationPort,
      SaveReservationPort saveReservationPort,
      PrepareReservationEscrowExecutionPort prepareReservationEscrowExecutionPort,
      CancelReservationEscrowExecutionPort cancelReservationEscrowExecutionPort,
      LoadReservationWalletPort loadReservationWalletPort,
      LoadReservationEscrowPaymentConfigPort loadReservationEscrowPaymentConfigPort,
      LoadReservationExecutionWritePort loadReservationExecutionWritePort,
      LoadReservationExecutionStatePort loadReservationExecutionStatePort,
      ReplayConfirmedReservationExecutionPort replayConfirmedReservationExecutionPort,
      LoadReservationEscrowOrderPort loadReservationEscrowOrderPort,
      LoadReservationEscrowPort loadReservationEscrowPort,
      SaveReservationActionStatePort saveReservationActionStatePort,
      LoadReservationActionStatePort loadReservationActionStatePort,
      BindReservationActionStatePort bindReservationActionStatePort,
      RecordTrainerStrikePort recordTrainerStrikePort,
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
    this.loadReservationExecutionWritePort =
        java.util.Objects.requireNonNull(loadReservationExecutionWritePort);
    this.loadReservationExecutionStatePort =
        java.util.Objects.requireNonNull(loadReservationExecutionStatePort);
    this.replayConfirmedReservationExecutionPort =
        java.util.Objects.requireNonNull(replayConfirmedReservationExecutionPort);
    this.loadReservationEscrowOrderPort =
        java.util.Objects.requireNonNull(loadReservationEscrowOrderPort);
    this.loadReservationEscrowPort = loadReservationEscrowPort;
    this.saveReservationActionStatePort = saveReservationActionStatePort;
    this.loadReservationActionStatePort = loadReservationActionStatePort;
    this.bindReservationActionStatePort = bindReservationActionStatePort;
    this.recordTrainerStrikePort = recordTrainerStrikePort;
    this.clock = clock;
  }

  public void setTransactionPort(RunReservationTransactionPort transactionPort) {
    this.transactionPort = java.util.Objects.requireNonNull(transactionPort);
  }

  @Override
  public ClaimExpiredRefundReservationResult execute(ClaimExpiredRefundReservationCommand command) {
    DeadlineRefundInspection inspected = runInTransaction(() -> inspectDeadlineRefund(command));
    if (inspected.confirmedState() != null) {
      return replayConfirmedState(inspected.reservation(), inspected.confirmedState());
    }
    if (inspected.existingWeb3() != null) {
      return replayConfirmedIfNeeded(inspected.reservation(), inspected.existingWeb3());
    }
    ChainSyncResult chainSync = syncChainStateBeforeClaim(inspected.reservation());
    if (chainSync.stop()) {
      recordTrainerRejectStrikeIfNeeded(chainSync.reservation());
      return result(chainSync.reservation(), null);
    }
    BeginDeadlineRefundResult beginResult =
        runInTransaction(() -> beginDeadlineRefund(command, chainSync.reservation()));
    if (beginResult.confirmedState() != null) {
      return replayConfirmedState(beginResult.reservation(), beginResult.confirmedState());
    }
    if (beginResult.existingWeb3() != null) {
      return replayConfirmedIfNeeded(beginResult.reservation(), beginResult.existingWeb3());
    }
    PendingPreparation phaseA = beginResult.pendingPreparation();
    PrepareReservationEscrowResult prepared;
    try {
      prepared =
          prepareReservationEscrowExecutionPort.prepareDeadlineRefund(phaseA.prepareCommand());
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
                      ReservationStatus.DEADLINE_REFUND_PENDING));
    } catch (RuntimeException e) {
      compensateBindFailure(phaseA, prepared, e);
      throw e;
    }

    log.info(
        "Reservation deadline refund intent prepared: id={}, userId={}, intentId={}",
        saved.getId(),
        command.userId(),
        prepared.web3().executionIntent().id());
    return result(saved, prepared.web3());
  }

  private DeadlineRefundInspection inspectDeadlineRefund(
      ClaimExpiredRefundReservationCommand command) {
    Reservation reservation =
        loadReservationPort
            .findByIdWithLock(command.reservationId())
            .orElseThrow(() -> new ReservationNotFoundException(command.reservationId()));

    if (!reservation.isOwnedByUser(command.userId())) {
      throw new MarketplaceUnauthorizedAccessException();
    }
    if (isUnboundDeadlineRefundPending(reservation)) {
      throw new MarketplaceReservationStateException(
          ErrorCode.MARKETPLACE_ACTIVE_EXECUTION_CONFLICT,
          "Deadline refund preparation is already in progress");
    }

    ReservationExecutionStateView currentState = loadCurrentExecutionStateIfPresent(reservation);
    if (currentState != null && !isRetryableTerminal(currentState.status())) {
      if (isConfirmedOutcome(currentState)) {
        return DeadlineRefundInspection.confirmed(reservation, currentState);
      }
      if (command.userId().equals(currentState.requesterUserId())) {
        return DeadlineRefundInspection.existing(
            reservation, loadOwnedCurrentExecution(command, reservation));
      }
      throw new MarketplaceReservationStateException(
          ErrorCode.MARKETPLACE_ACTIVE_EXECUTION_CONFLICT,
          "another marketplace execution is still active for this reservation");
    }
    return DeadlineRefundInspection.ready(reservation);
  }

  private BeginDeadlineRefundResult beginDeadlineRefund(
      ClaimExpiredRefundReservationCommand command, Reservation expected) {
    Reservation reservation =
        loadReservationPort
            .findByIdWithLock(expected.getId())
            .orElseThrow(() -> new ReservationNotFoundException(expected.getId()));
    if (!sameClaimSnapshot(reservation, expected)) {
      throw new MarketplaceReservationStateException(
          ErrorCode.MARKETPLACE_ACTIVE_EXECUTION_CONFLICT,
          "marketplace reservation state changed before deadline refund preparation");
    }
    return beginDeadlineRefundLocked(command, reservation);
  }

  private BeginDeadlineRefundResult beginDeadlineRefundLocked(
      ClaimExpiredRefundReservationCommand command, Reservation reservation) {
    if (!reservation.isOwnedByUser(command.userId())) {
      throw new MarketplaceUnauthorizedAccessException();
    }
    if (isUnboundDeadlineRefundPending(reservation)) {
      throw new MarketplaceReservationStateException(
          ErrorCode.MARKETPLACE_ACTIVE_EXECUTION_CONFLICT,
          "Deadline refund preparation is already in progress");
    }

    ReservationExecutionStateView currentState = loadCurrentExecutionStateIfPresent(reservation);
    if (currentState != null && !isRetryableTerminal(currentState.status())) {
      if (isConfirmedOutcome(currentState)) {
        return BeginDeadlineRefundResult.confirmed(reservation, currentState);
      }
      if (command.userId().equals(currentState.requesterUserId())) {
        return BeginDeadlineRefundResult.existing(
            reservation, loadOwnedCurrentExecution(command, reservation));
      }
      throw new MarketplaceReservationStateException(
          ErrorCode.MARKETPLACE_ACTIVE_EXECUTION_CONFLICT,
          "another marketplace execution is still active for this reservation");
    }

    Reservation refundable = ensureRefundAvailable(reservation, currentState != null);
    MarketplaceReservationEscrow escrow = loadEscrowProjection(refundable);
    staleRetryableCurrentActionStateIfNeeded(reservation, currentState);
    if (escrow != null) {
      ReservationEscrowActionGuard.requireActiveWalletMatchesSnapshot(
          loadReservationWalletPort, command.userId(), escrow.getBuyerWalletAddress());
    }
    Reservation pending =
        saveReservationPort.save(
            refundable.beginDeadlineRefundPending(UUID.randomUUID().toString()));
    MarketplaceReservationActionState actionState =
        createActionState(
            pending,
            escrow,
            ReservationEscrowAction.DEADLINE_REFUND,
            ReservationEscrowActorType.BUYER,
            command.userId(),
            ReservationStatus.DEADLINE_REFUND_AVAILABLE,
            ReservationEscrowStatus.DEADLINE_REFUND_AVAILABLE,
            null);
    return BeginDeadlineRefundResult.pending(
        new PendingPreparation(
            pending, commandFor(pending, escrow, actionState, command.userId()), actionState));
  }

  private void staleRetryableCurrentActionStateIfNeeded(
      Reservation reservation, ReservationExecutionStateView currentState) {
    if (currentState == null
        || !isRetryableTerminal(currentState.status())
        || loadReservationActionStatePort == null
        || saveReservationActionStatePort == null) {
      return;
    }
    loadReservationActionStatePort
        .findLatestByReservationIdWithLock(reservation.getId())
        .filter(state -> state.getStatus().isActive())
        .filter(
            state ->
                equalsNullable(
                        state.getExecutionIntentPublicId(),
                        reservation.getCurrentExecutionIntentPublicId())
                    || equalsNullable(
                        state.getExecutionIntentPublicId(), currentState.executionIntentId()))
        .ifPresent(
            state ->
                saveReservationActionStatePort.save(
                    state.toBuilder()
                        .status(ReservationActionStateStatus.STALE)
                        .retryable(false)
                        .errorCode("RETRY_SUPERSEDED")
                        .errorReason(
                            "marketplace deadline refund retry created a newer action-state")
                        .build()));
  }

  private ClaimExpiredRefundReservationResult replayConfirmedState(
      Reservation reservation, ReservationExecutionStateView state) {
    replayConfirmedReservationExecutionPort.replayConfirmed(
        state.executionIntentId(), state.actionType());
    Reservation latest = loadReservationPort.findById(reservation.getId()).orElse(reservation);
    return result(latest, null);
  }

  private ClaimExpiredRefundReservationResult replayConfirmedIfNeeded(
      Reservation reservation, ReservationExecutionWriteView web3) {
    if ("CONFIRMED".equals(web3.executionIntent().status())) {
      replayConfirmedReservationExecutionPort.replayConfirmed(
          web3.executionIntent().id(), web3.actionType());
      Reservation latest = loadReservationPort.findById(reservation.getId()).orElse(reservation);
      return result(latest, web3);
    }
    return result(reservation, web3);
  }

  private ChainSyncResult syncChainStateBeforeClaim(Reservation reservation) {
    ReservationEscrowOrderView order;
    try {
      order =
          loadReservationEscrowOrderPort.getOrder(
              ReservationOrderKeySupport.requireOrderKey(reservation));
    } catch (MarketplaceWeb3DisabledException e) {
      return ChainSyncResult.continueWith(reservation);
    }
    return runInTransaction(() -> syncLockedChainStateBeforeClaim(reservation, order));
  }

  private ChainSyncResult syncLockedChainStateBeforeClaim(
      Reservation expected, ReservationEscrowOrderView order) {
    Reservation reservation =
        loadReservationPort
            .findByIdWithLock(expected.getId())
            .orElseThrow(() -> new ReservationNotFoundException(expected.getId()));
    if (!sameClaimSnapshot(reservation, expected)) {
      return ChainSyncResult.continueWith(reservation);
    }
    if (order == null || order.isAbsent()) {
      throw new MarketplaceReservationStateException(
          ErrorCode.MARKETPLACE_DEADLINE_SYNC_REQUIRED,
          "Marketplace escrow order is absent and cannot be refunded safely");
    }
    LocalDateTime deadlineAt = deadlineAt(order.deadlineEpochSeconds());
    return switch (order.state()) {
      case ReservationEscrowOrderView.STATE_CREATED ->
          syncCreatedChainOrder(reservation, order, deadlineAt);
      case ReservationEscrowOrderView.STATE_CONFIRMED ->
          stopWith(
              reservation.syncChainOutcome(
                  ReservationStatus.SETTLED,
                  ReservationEscrowStatus.SETTLED,
                  reservation.getTxHash(),
                  order.deadlineEpochSeconds(),
                  deadlineAt));
      case ReservationEscrowOrderView.STATE_CANCELLED ->
          stopWith(syncCancelledChainOutcome(reservation, order, deadlineAt));
      case ReservationEscrowOrderView.STATE_ADMIN_SETTLED ->
          stopWith(
              reservation.syncChainOutcome(
                  ReservationStatus.AUTO_SETTLED,
                  ReservationEscrowStatus.SETTLED,
                  reservation.getTxHash(),
                  order.deadlineEpochSeconds(),
                  deadlineAt));
      case ReservationEscrowOrderView.STATE_ADMIN_REFUNDED ->
          stopWith(
              reservation.syncChainOutcome(
                  ReservationStatus.TIMEOUT_CANCELLED,
                  ReservationEscrowStatus.REFUNDED,
                  reservation.getTxHash(),
                  order.deadlineEpochSeconds(),
                  deadlineAt));
      case ReservationEscrowOrderView.STATE_DEADLINE_REFUNDED ->
          stopWith(
              reservation.syncChainOutcome(
                  ReservationStatus.DEADLINE_REFUNDED,
                  ReservationEscrowStatus.DEADLINE_REFUNDED,
                  reservation.getTxHash(),
                  order.deadlineEpochSeconds(),
                  deadlineAt));
      default ->
          throw new MarketplaceReservationStateException(
              ErrorCode.MARKETPLACE_DEADLINE_SYNC_REQUIRED,
              "Unsupported marketplace escrow order state: " + order.state());
    };
  }

  private ChainSyncResult syncCreatedChainOrder(
      Reservation reservation, ReservationEscrowOrderView order, LocalDateTime deadlineAt) {
    if (isUnboundDeadlineRefundPending(reservation)) {
      throw new MarketplaceReservationStateException(
          ErrorCode.MARKETPLACE_ACTIVE_EXECUTION_CONFLICT,
          "Deadline refund preparation is already in progress");
    }
    if (deadlineAt == null || !LocalDateTime.now(clock).isAfter(deadlineAt)) {
      throw new MarketplaceReservationStateException(
          ErrorCode.MARKETPLACE_DEADLINE_EXECUTION_WINDOW_EXPIRED,
          "Deadline refund is not yet available on the escrow contract");
    }
    Reservation available =
        reservation.getStatus() == ReservationStatus.DEADLINE_REFUND_AVAILABLE
            ? reservation.toBuilder()
                .contractDeadlineEpochSeconds(order.deadlineEpochSeconds())
                .contractDeadlineAt(deadlineAt)
                .build()
            : reservation.markDeadlineRefundAvailable(order.deadlineEpochSeconds(), deadlineAt);
    return ChainSyncResult.continueWith(saveReservationPort.save(available));
  }

  private Reservation syncCancelledChainOutcome(
      Reservation reservation, ReservationEscrowOrderView order, LocalDateTime deadlineAt) {
    if (!hasCancelActorEvidence(reservation)) {
      return reservation.syncChainOutcome(
          ReservationStatus.MANUAL_SYNC_REQUIRED,
          ReservationEscrowStatus.MANUAL_SYNC_REQUIRED,
          reservation.getTxHash(),
          order.deadlineEpochSeconds(),
          deadlineAt);
    }
    return reservation.syncChainOutcome(
        cancelledStatus(reservation),
        ReservationEscrowStatus.REFUNDED,
        reservation.getTxHash(),
        order.deadlineEpochSeconds(),
        deadlineAt);
  }

  private ReservationStatus cancelledStatus(Reservation reservation) {
    if (reservation.getStatus() == ReservationStatus.REJECTED) {
      return ReservationStatus.REJECTED;
    }
    if (reservation.getStatus() == ReservationStatus.USER_CANCELLED) {
      return ReservationStatus.USER_CANCELLED;
    }
    return reservation.getPendingAction() == ReservationEscrowAction.TRAINER_REJECT
            || reservation.getStatus() == ReservationStatus.REJECT_PENDING
        ? ReservationStatus.REJECTED
        : ReservationStatus.USER_CANCELLED;
  }

  private boolean hasCancelActorEvidence(Reservation reservation) {
    return reservation.getPendingAction() == ReservationEscrowAction.BUYER_CANCEL
        || reservation.getPendingAction() == ReservationEscrowAction.TRAINER_REJECT
        || reservation.getStatus() == ReservationStatus.CANCEL_PENDING
        || reservation.getStatus() == ReservationStatus.REJECT_PENDING
        || reservation.getStatus() == ReservationStatus.USER_CANCELLED
        || reservation.getStatus() == ReservationStatus.REJECTED;
  }

  private ChainSyncResult stopWith(Reservation reservation) {
    return ChainSyncResult.stop(saveReservationPort.save(reservation));
  }

  private LocalDateTime deadlineAt(Long epochSeconds) {
    if (epochSeconds == null) {
      return null;
    }
    return LocalDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), clock.getZone());
  }

  private boolean sameClaimSnapshot(Reservation current, Reservation expected) {
    return current.getStatus() == expected.getStatus()
        && equalsNullable(
            current.getCurrentExecutionIntentPublicId(),
            expected.getCurrentExecutionIntentPublicId())
        && equalsNullable(current.getPendingAttemptToken(), expected.getPendingAttemptToken());
  }

  private boolean isUnboundDeadlineRefundPending(Reservation reservation) {
    return reservation.getStatus() == ReservationStatus.DEADLINE_REFUND_PENDING
        && reservation.getCurrentExecutionIntentPublicId() == null;
  }

  private void recordTrainerRejectStrikeIfNeeded(Reservation reservation) {
    if (reservation.getStatus() != ReservationStatus.REJECTED || recordTrainerStrikePort == null) {
      return;
    }
    try {
      recordTrainerStrikePort.recordStrike(
          reservation.getTrainerId(),
          TrainerStrikeEvent.REASON_REJECT,
          RecordTrainerStrikePort.SOURCE_MARKETPLACE_RESERVATION_REJECT,
          String.valueOf(reservation.getId()));
    } catch (RuntimeException e) {
      log.warn(
          "Failed to record trainer reject strike after reservation chain sync: id={}, trainerId={}",
          reservation.getId(),
          reservation.getTrainerId(),
          e);
    }
  }

  private ReservationExecutionStateView loadCurrentExecutionStateIfPresent(
      Reservation reservation) {
    if (reservation.getCurrentExecutionIntentPublicId() == null) {
      return null;
    }
    return loadReservationExecutionStatePort.loadState(
        reservation.getCurrentExecutionIntentPublicId());
  }

  private ReservationExecutionWriteView loadOwnedCurrentExecution(
      ClaimExpiredRefundReservationCommand command, Reservation reservation) {
    return loadReservationExecutionWritePort
        .load(command.userId(), reservation.getCurrentExecutionIntentPublicId())
        .asExistingForOrder(ReservationOrderKeySupport.requireOrderKey(reservation));
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
          "Failed to cancel marketplace deadline refund intent after Phase B bind failure:"
              + " intentId={}",
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

  private boolean equalsNullable(Object left, Object right) {
    return left == null ? right == null : left.equals(right);
  }

  private <T> T runInTransaction(java.util.function.Supplier<T> supplier) {
    return transactionPort.requiresNew(supplier);
  }

  private ClaimExpiredRefundReservationResult result(
      Reservation reservation, ReservationExecutionWriteView web3) {
    return new ClaimExpiredRefundReservationResult(
        reservation.getId(),
        ReservationDisplayStatusMapper.displayStatus(reservation),
        ReservationDisplayStatusMapper.businessStatus(reservation),
        reservation.getEffectiveEscrowStatus().name(),
        web3);
  }

  private boolean isRetryableTerminal(String status) {
    return RETRYABLE_TERMINAL_STATUSES.contains(status);
  }

  private boolean isConfirmed(String status) {
    return "CONFIRMED".equals(status);
  }

  private boolean isConfirmedOutcome(ReservationExecutionStateView state) {
    return isConfirmed(state.status()) || "SUCCEEDED".equals(state.transactionStatus());
  }

  private Reservation ensureRefundAvailable(
      Reservation reservation, boolean currentIntentRetryableTerminal) {
    if (reservation.getStatus() == ReservationStatus.DEADLINE_REFUND_PENDING) {
      if (currentIntentRetryableTerminal) {
        return saveReservationPort.save(reservation.markDeadlineRefundAvailable());
      }
      throw new MarketplaceReservationStateException(
          ErrorCode.MARKETPLACE_ACTIVE_EXECUTION_CONFLICT,
          "Deadline refund execution is already pending");
    }
    if (reservation.getContractDeadlineAt() == null) {
      throw new MarketplaceReservationStateException(
          ErrorCode.MARKETPLACE_DEADLINE_SYNC_REQUIRED,
          "Contract deadline is not available for this reservation");
    }
    if (!LocalDateTime.now(clock).isAfter(reservation.getContractDeadlineAt())) {
      throw new MarketplaceReservationStateException(
          ErrorCode.MARKETPLACE_DEADLINE_EXECUTION_WINDOW_EXPIRED,
          "Deadline refund is only available after the contract deadline");
    }
    if (reservation.getStatus() == ReservationStatus.DEADLINE_REFUND_AVAILABLE) {
      return reservation;
    }
    if (!reservation.getStatus().canTransitionTo(ReservationStatus.DEADLINE_REFUND_AVAILABLE)) {
      throw new MarketplaceReservationStateException(
          ErrorCode.MARKETPLACE_RESERVATION_INVALID_STATUS,
          "Cannot claim deadline refund in status: " + reservation.getStatus());
    }
    return saveReservationPort.save(reservation.markDeadlineRefundAvailable());
  }

  private PrepareReservationEscrowCommand commandFor(
      Reservation reservation,
      MarketplaceReservationEscrow escrow,
      MarketplaceReservationActionState actionState,
      Long buyerUserId) {
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
        "BUYER",
        buyerUserId,
        buyerUserId,
        reservation.getTrainerId(),
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
        "DEADLINE_REFUNDED",
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
    if (!escrow.getEscrowFlow().isUserEip7702()) {
      throw new MarketplaceReservationStateException(
          ErrorCode.MARKETPLACE_RESERVATION_INVALID_STATUS,
          "Reservation escrow projection is not user-managed");
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
      MarketplaceReservationActionState actionState) {}

  private record DeadlineRefundInspection(
      Reservation reservation,
      ReservationExecutionWriteView existingWeb3,
      ReservationExecutionStateView confirmedState) {

    static DeadlineRefundInspection ready(Reservation reservation) {
      return new DeadlineRefundInspection(reservation, null, null);
    }

    static DeadlineRefundInspection existing(
        Reservation reservation, ReservationExecutionWriteView existingWeb3) {
      return new DeadlineRefundInspection(reservation, existingWeb3, null);
    }

    static DeadlineRefundInspection confirmed(
        Reservation reservation, ReservationExecutionStateView confirmedState) {
      return new DeadlineRefundInspection(reservation, null, confirmedState);
    }
  }

  private record ChainSyncResult(Reservation reservation, boolean stop) {

    static ChainSyncResult continueWith(Reservation reservation) {
      return new ChainSyncResult(reservation, false);
    }

    static ChainSyncResult stop(Reservation reservation) {
      return new ChainSyncResult(reservation, true);
    }
  }

  private record BeginDeadlineRefundResult(
      Reservation reservation,
      ReservationExecutionWriteView existingWeb3,
      ReservationExecutionStateView confirmedState,
      PendingPreparation pendingPreparation) {

    static BeginDeadlineRefundResult existing(
        Reservation reservation, ReservationExecutionWriteView existingWeb3) {
      return new BeginDeadlineRefundResult(reservation, existingWeb3, null, null);
    }

    static BeginDeadlineRefundResult confirmed(
        Reservation reservation, ReservationExecutionStateView confirmedState) {
      return new BeginDeadlineRefundResult(reservation, null, confirmedState, null);
    }

    static BeginDeadlineRefundResult pending(PendingPreparation pendingPreparation) {
      return new BeginDeadlineRefundResult(
          pendingPreparation.reservation(), null, null, pendingPreparation);
    }
  }
}
