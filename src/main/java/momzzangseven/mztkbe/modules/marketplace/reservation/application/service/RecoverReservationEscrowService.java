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
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.PrepareReservationEscrowCommand;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.PrepareReservationEscrowResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.RecoverReservationEscrowCommand;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.RecoverReservationEscrowResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationEscrowOrderView;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationExecutionStateView;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationExecutionWriteView;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.RecoverReservationEscrowUseCase;
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

/** Resumes or recreates user-owned marketplace execution intents for app re-entry. */
@Slf4j
public class RecoverReservationEscrowService implements RecoverReservationEscrowUseCase {

  private static final Set<String> RETRYABLE_TERMINAL_STATUSES =
      Set.of("FAILED_ONCHAIN", "EXPIRED", "CANCELED", "NONCE_STALE");

  private final LoadReservationPort loadReservationPort;
  private final SaveReservationPort saveReservationPort;
  private final PrepareReservationEscrowExecutionPort prepareReservationEscrowExecutionPort;
  private final CancelReservationEscrowExecutionPort cancelReservationEscrowExecutionPort;
  private final LoadReservationExecutionWritePort loadReservationExecutionWritePort;
  private final LoadReservationExecutionStatePort loadReservationExecutionStatePort;
  private final ReplayConfirmedReservationExecutionPort replayConfirmedReservationExecutionPort;
  private final LoadReservationWalletPort loadReservationWalletPort;
  private final LoadReservationEscrowPaymentConfigPort loadReservationEscrowPaymentConfigPort;
  private final LoadReservationEscrowOrderPort loadReservationEscrowOrderPort;
  private final LoadReservationEscrowPort loadReservationEscrowPort;
  private final SaveReservationActionStatePort saveReservationActionStatePort;
  private final LoadReservationActionStatePort loadReservationActionStatePort;
  private final BindReservationActionStatePort bindReservationActionStatePort;
  private final RecordTrainerStrikePort recordTrainerStrikePort;
  private final Clock clock;
  private RunReservationTransactionPort transactionPort;

  public RecoverReservationEscrowService(
      LoadReservationPort loadReservationPort,
      SaveReservationPort saveReservationPort,
      PrepareReservationEscrowExecutionPort prepareReservationEscrowExecutionPort,
      CancelReservationEscrowExecutionPort cancelReservationEscrowExecutionPort,
      LoadReservationExecutionWritePort loadReservationExecutionWritePort,
      LoadReservationExecutionStatePort loadReservationExecutionStatePort,
      ReplayConfirmedReservationExecutionPort replayConfirmedReservationExecutionPort,
      LoadReservationWalletPort loadReservationWalletPort,
      LoadReservationEscrowPaymentConfigPort loadReservationEscrowPaymentConfigPort,
      LoadReservationEscrowOrderPort loadReservationEscrowOrderPort,
      RecordTrainerStrikePort recordTrainerStrikePort,
      Clock clock) {
    this(
        loadReservationPort,
        saveReservationPort,
        prepareReservationEscrowExecutionPort,
        cancelReservationEscrowExecutionPort,
        loadReservationExecutionWritePort,
        loadReservationExecutionStatePort,
        replayConfirmedReservationExecutionPort,
        loadReservationWalletPort,
        loadReservationEscrowPaymentConfigPort,
        loadReservationEscrowOrderPort,
        null,
        null,
        null,
        null,
        recordTrainerStrikePort,
        clock);
  }

  public RecoverReservationEscrowService(
      LoadReservationPort loadReservationPort,
      SaveReservationPort saveReservationPort,
      PrepareReservationEscrowExecutionPort prepareReservationEscrowExecutionPort,
      CancelReservationEscrowExecutionPort cancelReservationEscrowExecutionPort,
      LoadReservationExecutionWritePort loadReservationExecutionWritePort,
      LoadReservationExecutionStatePort loadReservationExecutionStatePort,
      ReplayConfirmedReservationExecutionPort replayConfirmedReservationExecutionPort,
      LoadReservationWalletPort loadReservationWalletPort,
      LoadReservationEscrowPaymentConfigPort loadReservationEscrowPaymentConfigPort,
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
    this.loadReservationExecutionWritePort =
        java.util.Objects.requireNonNull(loadReservationExecutionWritePort);
    this.loadReservationExecutionStatePort =
        java.util.Objects.requireNonNull(loadReservationExecutionStatePort);
    this.replayConfirmedReservationExecutionPort =
        java.util.Objects.requireNonNull(replayConfirmedReservationExecutionPort);
    this.loadReservationWalletPort = java.util.Objects.requireNonNull(loadReservationWalletPort);
    this.loadReservationEscrowPaymentConfigPort =
        java.util.Objects.requireNonNull(loadReservationEscrowPaymentConfigPort);
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
  public RecoverReservationEscrowResult execute(RecoverReservationEscrowCommand command) {
    Reservation reservation =
        runInTransaction(
            () ->
                loadReservationPort
                    .findByIdWithLock(command.reservationId())
                    .orElseThrow(() -> new ReservationNotFoundException(command.reservationId())));
    RecoveryFlow flow = resolveFlow(reservation);
    if (reservation.getCurrentExecutionIntentPublicId() != null) {
      CurrentIntentResolution currentIntentResolution =
          resolveCurrentExecution(command, reservation, flow);
      if (currentIntentResolution.stop()) {
        return currentIntentResolution.result();
      }
      reservation = currentIntentResolution.reservation();
      flow = resolveFlow(reservation);
    }
    if (shouldForceDeadlineRefund(reservation, flow)) {
      RecoveryFlow expiredFlow = flow;
      Reservation expiredReservation = reservation;
      requireBuyerOwnedRefundRecovery(expiredFlow, command.requesterId(), expiredReservation);
      reservation = runInTransaction(() -> forceDeadlineRefundAvailable(expiredReservation));
      flow = resolveFlow(reservation);
    }
    validateActor(reservation, command.requesterId(), flow);

    ChainSyncResult chainSync = syncChainStateBeforePrepare(reservation, flow);
    if (chainSync.stop()) {
      recordTrainerRejectStrikeIfNeeded(chainSync.reservation());
      return result(chainSync.reservation(), null);
    }
    reservation = chainSync.reservation();
    flow = resolveFlow(reservation);
    guardUnboundDeadlineRefundPending(reservation);
    if (shouldForceDeadlineRefund(reservation, flow)) {
      RecoveryFlow expiredFlow = flow;
      Reservation expiredReservation = reservation;
      requireBuyerOwnedRefundRecovery(expiredFlow, command.requesterId(), expiredReservation);
      reservation = runInTransaction(() -> forceDeadlineRefundAvailable(expiredReservation));
      flow = resolveFlow(reservation);
    }
    validateActor(reservation, command.requesterId(), flow);

    return prepareRecovery(command.requesterId(), reservation, flow);
  }

  private CurrentIntentResolution resolveCurrentExecution(
      RecoverReservationEscrowCommand command, Reservation reservation, RecoveryFlow flow) {
    ReservationExecutionStateView state =
        loadReservationExecutionStatePort.loadState(
            reservation.getCurrentExecutionIntentPublicId());
    if (isConfirmedOutcome(state)) {
      validateParticipant(reservation, command.requesterId());
      replayConfirmedReservationExecutionPort.replayConfirmed(
          state.executionIntentId(), state.actionType());
      Reservation latest = loadReservationPort.findById(reservation.getId()).orElse(reservation);
      return CurrentIntentResolution.stop(result(latest, null));
    }
    if (shouldForceDeadlineRefund(reservation, flow)
        && reservation.isOwnedByUser(command.requesterId())) {
      if (isRetryableTerminal(state.status())) {
        return CurrentIntentResolution.continueWith(reservation);
      }
      if (command.requesterId().equals(state.requesterUserId())) {
        return resolveOwnedCurrentExecution(command, reservation);
      }
      throw new MarketplaceReservationStateException(
          ErrorCode.MARKETPLACE_ACTIVE_EXECUTION_CONFLICT,
          "another marketplace execution is still active for this reservation");
    }

    validateActor(reservation, command.requesterId(), flow);
    if (isRetryableTerminal(state.status())) {
      return CurrentIntentResolution.continueWith(reservation);
    }
    if (!command.requesterId().equals(state.requesterUserId())) {
      throw new MarketplaceReservationStateException(
          ErrorCode.MARKETPLACE_ACTIVE_EXECUTION_CONFLICT,
          "another marketplace execution owns this reservation action");
    }
    return resolveOwnedCurrentExecution(command, reservation);
  }

  private CurrentIntentResolution resolveOwnedCurrentExecution(
      RecoverReservationEscrowCommand command, Reservation reservation) {
    ReservationExecutionWriteView current =
        loadReservationExecutionWritePort
            .load(command.requesterId(), reservation.getCurrentExecutionIntentPublicId())
            .asExistingForOrder(reservation.getOrderKey());
    if ("CONFIRMED".equals(current.executionIntent().status())) {
      replayConfirmedReservationExecutionPort.replayConfirmed(
          current.executionIntent().id(), current.actionType());
      Reservation latest = loadReservationPort.findById(reservation.getId()).orElse(reservation);
      return CurrentIntentResolution.stop(result(latest, current));
    }
    return CurrentIntentResolution.stop(result(reservation, current));
  }

  private ChainSyncResult syncChainStateBeforePrepare(Reservation reservation, RecoveryFlow flow) {
    ReservationEscrowOrderView order;
    try {
      order =
          loadReservationEscrowOrderPort.getOrder(
              ReservationOrderKeySupport.requireOrderKey(reservation));
    } catch (MarketplaceWeb3DisabledException e) {
      return ChainSyncResult.continueWith(reservation);
    }
    return runInTransaction(() -> syncLockedChainStateBeforePrepare(reservation, flow, order));
  }

  private ChainSyncResult syncLockedChainStateBeforePrepare(
      Reservation expected, RecoveryFlow flow, ReservationEscrowOrderView order) {
    Reservation reservation =
        loadReservationPort
            .findByIdWithLock(expected.getId())
            .orElseThrow(() -> new ReservationNotFoundException(expected.getId()));
    if (!sameRecoverySnapshot(reservation, expected)) {
      return ChainSyncResult.continueWith(reservation);
    }
    if (order == null || order.isAbsent()) {
      if (isUnboundDeadlineRefundPending(reservation)) {
        guardUnboundDeadlineRefundPending(reservation);
      }
      if (flow.action() == RecoveryAction.PURCHASE) {
        return ChainSyncResult.continueWith(reservation);
      }
      throw new MarketplaceReservationStateException(
          ErrorCode.MARKETPLACE_DEADLINE_SYNC_REQUIRED,
          "Marketplace escrow order is absent and cannot be recovered safely");
    }
    LocalDateTime deadlineAt = deadlineAt(order.deadlineEpochSeconds());
    return switch (order.state()) {
      case ReservationEscrowOrderView.STATE_CREATED ->
          syncCreatedChainOrder(reservation, flow, order, deadlineAt);
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

  private boolean sameRecoverySnapshot(Reservation current, Reservation expected) {
    return current.getStatus() == expected.getStatus()
        && equalsNullable(
            current.getCurrentExecutionIntentPublicId(),
            expected.getCurrentExecutionIntentPublicId())
        && equalsNullable(current.getPendingAttemptToken(), expected.getPendingAttemptToken());
  }

  private ChainSyncResult syncCreatedChainOrder(
      Reservation reservation,
      RecoveryFlow flow,
      ReservationEscrowOrderView order,
      LocalDateTime deadlineAt) {
    if (flow.action() == RecoveryAction.PURCHASE) {
      Reservation synced =
          deadlineAt != null && reservation.sessionEndAt().plusHours(24).isAfter(deadlineAt)
              ? reservation.markPurchaseDeadlineRecoveryRequired(
                  order.deadlineEpochSeconds(), deadlineAt)
              : reservation.markPurchaseConfirmedLocked(order.deadlineEpochSeconds(), deadlineAt);
      return stopWith(synced);
    }
    if (flow.action() == RecoveryAction.DEADLINE_REFUND) {
      guardUnboundDeadlineRefundPending(reservation);
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
    return ChainSyncResult.continueWith(reservation);
  }

  private ChainSyncResult stopWith(Reservation reservation) {
    return ChainSyncResult.stop(saveReservationPort.save(reservation));
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

  private LocalDateTime deadlineAt(Long epochSeconds) {
    if (epochSeconds == null) {
      return null;
    }
    return LocalDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), clock.getZone());
  }

  private RecoverReservationEscrowResult prepareRecovery(
      Long requesterId, Reservation reservation, RecoveryFlow flow) {
    PreparedLocalState localState =
        runInTransaction(() -> ensurePreparedLocalState(reservation, flow));
    Reservation current = localState.reservation();
    PrepareReservationEscrowResult prepared;
    try {
      prepared =
          switch (flow.action()) {
            case PURCHASE ->
                prepareReservationEscrowExecutionPort.preparePurchase(
                    commandFor(
                        current,
                        localState.escrow(),
                        localState.actionState(),
                        "BUYER",
                        requesterId,
                        current.getTrainerId(),
                        "PENDING"));
            case BUYER_CANCEL ->
                prepareReservationEscrowExecutionPort.prepareCancel(
                    commandFor(
                        current,
                        localState.escrow(),
                        localState.actionState(),
                        "BUYER",
                        requesterId,
                        current.getTrainerId(),
                        "USER_CANCELLED"));
            case TRAINER_REJECT ->
                prepareReservationEscrowExecutionPort.prepareCancel(
                    commandFor(
                        current,
                        localState.escrow(),
                        localState.actionState(),
                        "TRAINER",
                        requesterId,
                        current.getUserId(),
                        "REJECTED"));
            case BUYER_CONFIRM ->
                prepareReservationEscrowExecutionPort.prepareConfirm(
                    commandFor(
                        current,
                        localState.escrow(),
                        localState.actionState(),
                        "BUYER",
                        requesterId,
                        current.getTrainerId(),
                        "SETTLED"));
            case DEADLINE_REFUND ->
                prepareReservationEscrowExecutionPort.prepareDeadlineRefund(
                    commandFor(
                        current,
                        localState.escrow(),
                        localState.actionState(),
                        "BUYER",
                        requesterId,
                        current.getTrainerId(),
                        "DEADLINE_REFUNDED"));
          };
    } catch (RuntimeException e) {
      rollbackPrepareFailure(localState, flow, e);
      throw e;
    }
    Reservation saved;
    try {
      saved = runInTransaction(() -> bindPreparedRecovery(localState, flow, prepared));
    } catch (RuntimeException e) {
      compensateBindFailure(localState, flow, prepared, e);
      throw e;
    }
    log.info(
        "Reservation recovery intent prepared: id={}, requesterId={}, action={}, intentId={}",
        saved.getId(),
        requesterId,
        flow.action(),
        prepared.web3().executionIntent().id());
    return result(saved, prepared.web3());
  }

  private Reservation bindPreparedRecovery(
      PreparedLocalState localState, RecoveryFlow flow, PrepareReservationEscrowResult prepared) {
    Reservation expected = localState.reservation();
    Reservation current =
        loadReservationPort
            .findByIdWithLock(expected.getId())
            .orElseThrow(() -> new ReservationNotFoundException(expected.getId()));
    validateRecoveryBindSnapshot(current, expected);
    bindActionState(localState, prepared.web3().executionIntent().id());
    return saveReservationPort.save(bind(current, flow, prepared));
  }

  private void validateRecoveryBindSnapshot(Reservation current, Reservation expected) {
    if (current.getStatus() != expected.getStatus()
        || !equalsNullable(current.getPendingAttemptToken(), expected.getPendingAttemptToken())) {
      throw new MarketplaceReservationStateException(
          ErrorCode.MARKETPLACE_ACTIVE_EXECUTION_CONFLICT,
          "marketplace reservation state changed before recovered execution intent bind");
    }
  }

  private void compensateBindFailure(
      PreparedLocalState localState,
      RecoveryFlow flow,
      PrepareReservationEscrowResult prepared,
      RuntimeException cause) {
    if (cancelSignablePreparedIntent(prepared, cause)) {
      rollbackRecoveredPending(localState, flow, cause);
    }
  }

  private boolean cancelSignablePreparedIntent(
      PrepareReservationEscrowResult prepared, RuntimeException cause) {
    String executionIntentId = prepared.web3().executionIntent().id();
    try {
      return cancelReservationEscrowExecutionPort.cancelSignableIntent(
          executionIntentId,
          "MARKETPLACE_RECOVERY_BIND_FAILED",
          cause.getMessage() == null ? "marketplace recovery bind failed" : cause.getMessage());
    } catch (RuntimeException cancelFailure) {
      log.warn(
          "Failed to cancel marketplace recovery intent after Phase B bind failure: intentId={}",
          executionIntentId,
          cancelFailure);
      return false;
    }
  }

  private void rollbackRecoveredPending(
      PreparedLocalState localState, RecoveryFlow flow, RuntimeException cause) {
    Reservation expected = localState.reservation();
    runInTransaction(
        () -> {
          loadReservationPort
              .findByIdWithLock(expected.getId())
              .filter(
                  current ->
                      current.getStatus() == expected.getStatus()
                          && equalsNullable(
                              current.getPendingAttemptToken(), expected.getPendingAttemptToken()))
              .ifPresent(
                  current -> {
                    Reservation rollback =
                        flow.action() == RecoveryAction.PURCHASE
                            ? current.markPaymentFailed(
                                cause.getClass().getSimpleName(), cause.getMessage())
                            : current.rollbackToPriorState();
                    saveReservationPort.save(rollback);
                  });
          markActionStatePreparationFailed(
              localState, "ROLLBACK", "marketplace recovery rolled back");
          return null;
        });
  }

  private void rollbackPrepareFailure(
      PreparedLocalState localState, RecoveryFlow flow, RuntimeException cause) {
    if (!localState.newPendingTransition()) {
      if (localState.newActionState()) {
        markActionStatePreparationFailed(
            localState, cause.getClass().getSimpleName(), cause.getMessage());
      }
      return;
    }
    rollbackRecoveredPending(localState, flow, cause);
  }

  private PreparedLocalState ensurePreparedLocalState(Reservation reservation, RecoveryFlow flow) {
    MarketplaceReservationEscrow escrow = loadEscrowProjection(reservation);
    if (flow.action() != RecoveryAction.DEADLINE_REFUND) {
      MarketplaceReservationActionState actionState =
          findMatchingActionState(reservation, toEscrowAction(flow.action())).orElse(null);
      if (actionState == null && flow.action() != RecoveryAction.PURCHASE) {
        actionState =
            createActionState(
                reservation,
                escrow,
                toEscrowAction(flow.action()),
                actorType(flow.action()),
                actorUserId(reservation, flow),
                expectedReservationStatus(flow.action()),
                ReservationEscrowStatus.LOCKED,
                reservation.getRejectionReason());
        return PreparedLocalState.unchangedWithNewActionState(reservation, escrow, actionState);
      }
      return PreparedLocalState.unchanged(reservation, escrow, actionState);
    }
    if (reservation.getStatus() == ReservationStatus.DEADLINE_REFUND_PENDING) {
      return PreparedLocalState.unchanged(
          reservation,
          escrow,
          findMatchingActionState(reservation, ReservationEscrowAction.DEADLINE_REFUND)
              .orElse(null));
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
    if (reservation.getStatus() != ReservationStatus.DEADLINE_REFUND_AVAILABLE) {
      reservation = saveReservationPort.save(reservation.markDeadlineRefundAvailable());
    }
    Reservation pending =
        saveReservationPort.save(
            reservation.beginDeadlineRefundPending(UUID.randomUUID().toString()));
    MarketplaceReservationActionState actionState =
        createActionState(
            pending,
            escrow,
            ReservationEscrowAction.DEADLINE_REFUND,
            ReservationEscrowActorType.BUYER,
            pending.getUserId(),
            ReservationStatus.DEADLINE_REFUND_AVAILABLE,
            ReservationEscrowStatus.DEADLINE_REFUND_AVAILABLE,
            null);
    return PreparedLocalState.newPending(pending, escrow, actionState);
  }

  private Reservation bind(
      Reservation reservation, RecoveryFlow flow, PrepareReservationEscrowResult prepared) {
    String intentId = prepared.web3().executionIntent().id();
    if (flow.action() == RecoveryAction.PURCHASE) {
      if (reservation.getStatus() == ReservationStatus.HOLDING
          || reservation.getStatus() == ReservationStatus.PURCHASE_PREPARING) {
        return reservation.bindPurchaseIntent(intentId);
      }
      return reservation.toBuilder().currentExecutionIntentPublicId(intentId).build();
    }
    return reservation.bindPendingExecutionIntent(intentId);
  }

  private RecoveryFlow resolveFlow(Reservation reservation) {
    if (isPurchaseRecoveryState(reservation)) {
      return new RecoveryFlow(RecoveryAction.PURCHASE, true);
    }
    return switch (reservation.getStatus()) {
      case CANCEL_PENDING -> new RecoveryFlow(RecoveryAction.BUYER_CANCEL, true);
      case REJECT_PENDING -> new RecoveryFlow(RecoveryAction.TRAINER_REJECT, false);
      case CONFIRM_PENDING -> new RecoveryFlow(RecoveryAction.BUYER_CONFIRM, true);
      case DEADLINE_REFUND_PENDING, DEADLINE_REFUND_AVAILABLE ->
          new RecoveryFlow(RecoveryAction.DEADLINE_REFUND, true);
      case PENDING, APPROVED, DEADLINE_RECOVERY_REQUIRED ->
          expired(reservation)
              ? new RecoveryFlow(RecoveryAction.DEADLINE_REFUND, true)
              : throwInvalidRecovery(reservation);
      case DEADLINE_SYNC_REQUIRED ->
          throw new MarketplaceReservationStateException(
              ErrorCode.MARKETPLACE_DEADLINE_SYNC_REQUIRED,
              "Reservation requires deadline synchronization before recovery");
      default -> throwInvalidRecovery(reservation);
    };
  }

  private boolean isPurchaseRecoveryState(Reservation reservation) {
    return reservation.getStatus() == ReservationStatus.PURCHASE_PREPARING
        || reservation.getStatus() == ReservationStatus.PURCHASE_PENDING
        || (reservation.getStatus() == ReservationStatus.HOLDING
            && (reservation.getEffectiveEscrowStatus() == ReservationEscrowStatus.PURCHASE_PREPARING
                || reservation.getEffectiveEscrowStatus()
                    == ReservationEscrowStatus.PURCHASE_PENDING));
  }

  private RecoveryFlow throwInvalidRecovery(Reservation reservation) {
    throw new MarketplaceReservationStateException(
        ErrorCode.MARKETPLACE_RESERVATION_INVALID_STATUS,
        "Cannot recover reservation in status: " + reservation.getStatus());
  }

  private boolean expired(Reservation reservation) {
    return reservation.getContractDeadlineAt() != null
        && LocalDateTime.now(clock).isAfter(reservation.getContractDeadlineAt());
  }

  private boolean shouldForceDeadlineRefund(Reservation reservation, RecoveryFlow flow) {
    return expired(reservation)
        && switch (flow.action()) {
          case BUYER_CANCEL, TRAINER_REJECT, BUYER_CONFIRM -> true;
          default -> false;
        };
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

  private void requireBuyerOwnedRefundRecovery(
      RecoveryFlow expiredFlow, Long requesterId, Reservation reservation) {
    if (reservation.isOwnedByUser(requesterId)) {
      return;
    }
    if (expiredFlow.action() == RecoveryAction.TRAINER_REJECT) {
      throw new MarketplaceReservationStateException(
          ErrorCode.MARKETPLACE_DEADLINE_REFUND_REQUIRED,
          "Reservation deadline expired; buyer deadline refund is required");
    }
    validateActor(reservation, requesterId, expiredFlow);
  }

  private void guardUnboundDeadlineRefundPending(Reservation reservation) {
    if (!isUnboundDeadlineRefundPending(reservation)) {
      return;
    }
    throw new MarketplaceReservationStateException(
        ErrorCode.MARKETPLACE_ACTIVE_EXECUTION_CONFLICT,
        "Deadline refund preparation is already in progress");
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

  private Reservation forceDeadlineRefundAvailable(Reservation expected) {
    Reservation current =
        loadReservationPort
            .findByIdWithLock(expected.getId())
            .orElseThrow(() -> new ReservationNotFoundException(expected.getId()));
    RecoveryFlow flow = resolveFlow(current);
    if (!shouldForceDeadlineRefund(current, flow)) {
      return current;
    }
    return saveReservationPort.save(current.markDeadlineRefundAvailable());
  }

  private void validateActor(Reservation reservation, Long requesterId, RecoveryFlow flow) {
    boolean allowed =
        flow.buyerOwned()
            ? reservation.isOwnedByUser(requesterId)
            : reservation.isOwnedByTrainer(requesterId);
    if (!allowed) {
      throw new MarketplaceUnauthorizedAccessException();
    }
  }

  private void validateParticipant(Reservation reservation, Long requesterId) {
    if (!reservation.isOwnedByUser(requesterId) && !reservation.isOwnedByTrainer(requesterId)) {
      throw new MarketplaceUnauthorizedAccessException();
    }
  }

  private RecoverReservationEscrowResult result(
      Reservation reservation, ReservationExecutionWriteView web3) {
    return new RecoverReservationEscrowResult(
        reservation.getId(),
        ReservationDisplayStatusMapper.displayStatus(reservation),
        ReservationDisplayStatusMapper.businessStatus(reservation),
        reservation.getEffectiveEscrowStatus().name(),
        web3);
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
    if (!escrow.getEscrowFlow().isUserEip7702()) {
      throw new MarketplaceReservationStateException(
          ErrorCode.MARKETPLACE_RESERVATION_INVALID_STATUS,
          "Reservation escrow projection is not user-managed");
    }
    return escrow;
  }

  private java.util.Optional<MarketplaceReservationActionState> findMatchingActionState(
      Reservation reservation, ReservationEscrowAction action) {
    if (loadReservationActionStatePort == null || reservation.getPendingAttemptToken() == null) {
      return java.util.Optional.empty();
    }
    return loadReservationActionStatePort
        .findLatestByReservationIdAndActionType(reservation.getId(), action)
        .filter(
            state ->
                equalsNullable(state.getAttemptToken(), reservation.getPendingAttemptToken())
                    && state.getReservationId().equals(reservation.getId()));
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

  private void bindActionState(PreparedLocalState localState, String executionIntentId) {
    if (localState.actionState() == null || bindReservationActionStatePort == null) {
      return;
    }
    bindReservationActionStatePort
        .bindExecutionIntent(
            localState.actionState().getId(),
            localState.actionState().getAttemptToken(),
            executionIntentId)
        .orElseThrow(
            () ->
                new MarketplaceReservationStateException(
                    ErrorCode.MARKETPLACE_ACTIVE_EXECUTION_CONFLICT,
                    "marketplace reservation action state changed before execution intent bind"));
  }

  private void markActionStatePreparationFailed(
      PreparedLocalState localState, String errorCode, String errorReason) {
    if (localState.actionState() == null || saveReservationActionStatePort == null) {
      return;
    }
    saveReservationActionStatePort.save(
        localState.actionState().toBuilder()
            .status(ReservationActionStateStatus.PREPARATION_FAILED)
            .retryable(false)
            .errorCode(errorCode)
            .errorReason(errorReason)
            .build());
  }

  private ReservationEscrowAction toEscrowAction(RecoveryAction action) {
    return switch (action) {
      case PURCHASE -> ReservationEscrowAction.PURCHASE;
      case BUYER_CANCEL -> ReservationEscrowAction.BUYER_CANCEL;
      case TRAINER_REJECT -> ReservationEscrowAction.TRAINER_REJECT;
      case BUYER_CONFIRM -> ReservationEscrowAction.BUYER_CONFIRM;
      case DEADLINE_REFUND -> ReservationEscrowAction.DEADLINE_REFUND;
    };
  }

  private ReservationEscrowActorType actorType(RecoveryAction action) {
    return action == RecoveryAction.TRAINER_REJECT
        ? ReservationEscrowActorType.TRAINER
        : ReservationEscrowActorType.BUYER;
  }

  private Long actorUserId(Reservation reservation, RecoveryFlow flow) {
    return flow.buyerOwned() ? reservation.getUserId() : reservation.getTrainerId();
  }

  private ReservationStatus expectedReservationStatus(RecoveryAction action) {
    return switch (action) {
      case BUYER_CONFIRM -> ReservationStatus.APPROVED;
      case DEADLINE_REFUND -> ReservationStatus.DEADLINE_REFUND_AVAILABLE;
      default -> ReservationStatus.PENDING;
    };
  }

  private boolean equalsNullable(Object left, Object right) {
    return left == null ? right == null : left.equals(right);
  }

  private <T> T runInTransaction(java.util.function.Supplier<T> supplier) {
    return transactionPort.requiresNew(supplier);
  }

  private enum RecoveryAction {
    PURCHASE,
    BUYER_CANCEL,
    TRAINER_REJECT,
    BUYER_CONFIRM,
    DEADLINE_REFUND
  }

  private record RecoveryFlow(RecoveryAction action, boolean buyerOwned) {}

  private record ChainSyncResult(Reservation reservation, boolean stop) {

    static ChainSyncResult continueWith(Reservation reservation) {
      return new ChainSyncResult(reservation, false);
    }

    static ChainSyncResult stop(Reservation reservation) {
      return new ChainSyncResult(reservation, true);
    }
  }

  private record CurrentIntentResolution(
      Reservation reservation, RecoverReservationEscrowResult result, boolean stop) {

    static CurrentIntentResolution continueWith(Reservation reservation) {
      return new CurrentIntentResolution(reservation, null, false);
    }

    static CurrentIntentResolution stop(RecoverReservationEscrowResult result) {
      return new CurrentIntentResolution(null, result, true);
    }
  }

  private record PreparedLocalState(
      Reservation reservation,
      MarketplaceReservationEscrow escrow,
      MarketplaceReservationActionState actionState,
      boolean newPendingTransition,
      boolean newActionState) {

    static PreparedLocalState unchanged(
        Reservation reservation,
        MarketplaceReservationEscrow escrow,
        MarketplaceReservationActionState actionState) {
      return new PreparedLocalState(reservation, escrow, actionState, false, false);
    }

    static PreparedLocalState unchangedWithNewActionState(
        Reservation reservation,
        MarketplaceReservationEscrow escrow,
        MarketplaceReservationActionState actionState) {
      return new PreparedLocalState(reservation, escrow, actionState, false, true);
    }

    static PreparedLocalState newPending(
        Reservation reservation,
        MarketplaceReservationEscrow escrow,
        MarketplaceReservationActionState actionState) {
      return new PreparedLocalState(reservation, escrow, actionState, true, actionState != null);
    }
  }
}
