package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;
import momzzangseven.mztkbe.global.error.marketplace.MarketplaceUnauthorizedAccessException;
import momzzangseven.mztkbe.global.error.marketplace.MarketplaceWeb3DisabledException;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ClaimExpiredRefundReservationCommand;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ClaimExpiredRefundReservationResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.PrepareReservationEscrowCommand;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.PrepareReservationEscrowResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationEscrowOrderView;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationExecutionStateView;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationExecutionWriteView;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.ClaimExpiredRefundReservationUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.CancelReservationEscrowExecutionPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationEscrowOrderPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationEscrowPaymentConfigPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationExecutionStatePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationExecutionWritePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationWalletPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.PrepareReservationEscrowExecutionPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.RecordTrainerStrikePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.ReplayConfirmedReservationExecutionPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SaveReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.Reservation;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowAction;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.TrainerStrikeEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

/** Prepares the buyer-owned claimExpiredRefund marketplace execution. */
@Slf4j
@Service
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
  @Nullable private final RecordTrainerStrikePort recordTrainerStrikePort;
  private final Clock clock;
  private TransactionOperations transactionOperations;

  public ClaimExpiredRefundReservationService(
      LoadReservationPort loadReservationPort,
      SaveReservationPort saveReservationPort,
      @Nullable PrepareReservationEscrowExecutionPort prepareReservationEscrowExecutionPort,
      @Nullable CancelReservationEscrowExecutionPort cancelReservationEscrowExecutionPort,
      @Nullable LoadReservationWalletPort loadReservationWalletPort,
      @Nullable LoadReservationEscrowPaymentConfigPort loadReservationEscrowPaymentConfigPort,
      @Nullable LoadReservationExecutionWritePort loadReservationExecutionWritePort,
      @Nullable LoadReservationExecutionStatePort loadReservationExecutionStatePort,
      @Nullable ReplayConfirmedReservationExecutionPort replayConfirmedReservationExecutionPort,
      @Nullable LoadReservationEscrowOrderPort loadReservationEscrowOrderPort,
      @Nullable RecordTrainerStrikePort recordTrainerStrikePort,
      Clock clock) {
    this.loadReservationPort = loadReservationPort;
    this.saveReservationPort = saveReservationPort;
    this.prepareReservationEscrowExecutionPort =
        prepareReservationEscrowExecutionPort == null
            ? DisabledReservationWeb3PortFactory.prepareExecution()
            : prepareReservationEscrowExecutionPort;
    this.cancelReservationEscrowExecutionPort =
        cancelReservationEscrowExecutionPort == null
            ? DisabledReservationWeb3PortFactory.cancelExecution()
            : cancelReservationEscrowExecutionPort;
    this.loadReservationWalletPort =
        loadReservationWalletPort == null
            ? DisabledReservationWeb3PortFactory.wallet()
            : loadReservationWalletPort;
    this.loadReservationEscrowPaymentConfigPort =
        loadReservationEscrowPaymentConfigPort == null
            ? DisabledReservationWeb3PortFactory.paymentConfig()
            : loadReservationEscrowPaymentConfigPort;
    this.loadReservationExecutionWritePort =
        loadReservationExecutionWritePort == null
            ? DisabledReservationWeb3PortFactory.executionWrite()
            : loadReservationExecutionWritePort;
    this.loadReservationExecutionStatePort =
        loadReservationExecutionStatePort == null
            ? DisabledReservationWeb3PortFactory.executionState()
            : loadReservationExecutionStatePort;
    this.replayConfirmedReservationExecutionPort =
        replayConfirmedReservationExecutionPort == null
            ? DisabledReservationWeb3PortFactory.confirmedReplay()
            : replayConfirmedReservationExecutionPort;
    this.loadReservationEscrowOrderPort =
        loadReservationEscrowOrderPort == null
            ? DisabledReservationWeb3PortFactory.escrowOrder()
            : loadReservationEscrowOrderPort;
    this.recordTrainerStrikePort = recordTrainerStrikePort;
    this.clock = clock;
  }

  @Autowired
  void setTransactionManager(PlatformTransactionManager transactionManager) {
    TransactionTemplate template = new TransactionTemplate(transactionManager);
    template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    this.transactionOperations = template;
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
            .orElseThrow(
                () ->
                    new BusinessException(
                        ErrorCode.MARKETPLACE_RESERVATION_NOT_FOUND,
                        "Reservation not found: " + command.reservationId()));

    if (!reservation.isOwnedByUser(command.userId())) {
      throw new MarketplaceUnauthorizedAccessException();
    }
    if (isUnboundDeadlineRefundPending(reservation)) {
      throw new BusinessException(
          ErrorCode.MARKETPLACE_ACTIVE_EXECUTION_CONFLICT,
          "Deadline refund preparation is already in progress");
    }

    ReservationExecutionStateView currentState = loadCurrentExecutionStateIfPresent(reservation);
    if (currentState != null && !isRetryableTerminal(currentState.status())) {
      if (isConfirmed(currentState.status())) {
        return DeadlineRefundInspection.confirmed(reservation, currentState);
      }
      if (command.userId().equals(currentState.requesterUserId())) {
        return DeadlineRefundInspection.existing(
            reservation, loadOwnedCurrentExecution(command, reservation));
      }
      throw new BusinessException(
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
            .orElseThrow(
                () ->
                    new BusinessException(
                        ErrorCode.MARKETPLACE_RESERVATION_NOT_FOUND,
                        "Reservation not found: " + expected.getId()));
    if (!sameClaimSnapshot(reservation, expected)) {
      throw new BusinessException(
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
      throw new BusinessException(
          ErrorCode.MARKETPLACE_ACTIVE_EXECUTION_CONFLICT,
          "Deadline refund preparation is already in progress");
    }

    ReservationExecutionStateView currentState = loadCurrentExecutionStateIfPresent(reservation);
    if (currentState != null && !isRetryableTerminal(currentState.status())) {
      if (isConfirmed(currentState.status())) {
        return BeginDeadlineRefundResult.confirmed(reservation, currentState);
      }
      if (command.userId().equals(currentState.requesterUserId())) {
        return BeginDeadlineRefundResult.existing(
            reservation, loadOwnedCurrentExecution(command, reservation));
      }
      throw new BusinessException(
          ErrorCode.MARKETPLACE_ACTIVE_EXECUTION_CONFLICT,
          "another marketplace execution is still active for this reservation");
    }

    Reservation refundable = ensureRefundAvailable(reservation, currentState != null);
    Reservation pending =
        saveReservationPort.save(
            refundable.beginDeadlineRefundPending(UUID.randomUUID().toString()));
    return BeginDeadlineRefundResult.pending(
        new PendingPreparation(pending, commandFor(pending, command.userId())));
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
      order = loadReservationEscrowOrderPort.getOrder(ensureOrderKey(reservation));
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
            .orElseThrow(
                () ->
                    new BusinessException(
                        ErrorCode.MARKETPLACE_RESERVATION_NOT_FOUND,
                        "Reservation not found: " + expected.getId()));
    if (!sameClaimSnapshot(reservation, expected)) {
      return ChainSyncResult.continueWith(reservation);
    }
    if (order == null || order.isAbsent()) {
      throw new BusinessException(
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
          throw new BusinessException(
              ErrorCode.MARKETPLACE_DEADLINE_SYNC_REQUIRED,
              "Unsupported marketplace escrow order state: " + order.state());
    };
  }

  private ChainSyncResult syncCreatedChainOrder(
      Reservation reservation, ReservationEscrowOrderView order, LocalDateTime deadlineAt) {
    if (isUnboundDeadlineRefundPending(reservation)) {
      throw new BusinessException(
          ErrorCode.MARKETPLACE_ACTIVE_EXECUTION_CONFLICT,
          "Deadline refund preparation is already in progress");
    }
    if (deadlineAt == null || !LocalDateTime.now(clock).isAfter(deadlineAt)) {
      throw new BusinessException(
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

  @Nullable
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
        .asExistingForOrder(ensureOrderKey(reservation));
  }

  private Reservation bindPendingExecution(
      PendingPreparation phaseA, String executionIntentId, ReservationStatus expectedStatus) {
    Reservation current =
        loadReservationPort
            .findByIdWithLock(phaseA.reservation().getId())
            .orElseThrow(
                () ->
                    new BusinessException(
                        ErrorCode.MARKETPLACE_RESERVATION_NOT_FOUND,
                        "Reservation not found: " + phaseA.reservation().getId()));
    validatePendingSnapshot(current, phaseA.reservation(), expectedStatus);
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
      throw new BusinessException(
          ErrorCode.MARKETPLACE_ACTIVE_EXECUTION_CONFLICT,
          "marketplace reservation state changed before execution intent bind");
    }
  }

  private boolean equalsNullable(Object left, Object right) {
    return left == null ? right == null : left.equals(right);
  }

  private <T> T runInTransaction(java.util.function.Supplier<T> supplier) {
    if (transactionOperations == null) {
      return supplier.get();
    }
    return transactionOperations.execute(status -> supplier.get());
  }

  private ClaimExpiredRefundReservationResult result(
      Reservation reservation, ReservationExecutionWriteView web3) {
    return new ClaimExpiredRefundReservationResult(
        reservation.getId(),
        reservation.getStatus(),
        reservation.getEffectiveEscrowStatus().name(),
        web3);
  }

  private boolean isRetryableTerminal(String status) {
    return RETRYABLE_TERMINAL_STATUSES.contains(status);
  }

  private boolean isConfirmed(String status) {
    return "CONFIRMED".equals(status);
  }

  private Reservation ensureRefundAvailable(
      Reservation reservation, boolean currentIntentRetryableTerminal) {
    if (reservation.getStatus() == ReservationStatus.DEADLINE_REFUND_PENDING) {
      if (currentIntentRetryableTerminal) {
        return saveReservationPort.save(reservation.markDeadlineRefundAvailable());
      }
      throw new BusinessException(
          ErrorCode.MARKETPLACE_ACTIVE_EXECUTION_CONFLICT,
          "Deadline refund execution is already pending");
    }
    if (reservation.getContractDeadlineAt() == null) {
      throw new BusinessException(
          ErrorCode.MARKETPLACE_DEADLINE_SYNC_REQUIRED,
          "Contract deadline is not available for this reservation");
    }
    if (!LocalDateTime.now(clock).isAfter(reservation.getContractDeadlineAt())) {
      throw new BusinessException(
          ErrorCode.MARKETPLACE_DEADLINE_EXECUTION_WINDOW_EXPIRED,
          "Deadline refund is only available after the contract deadline");
    }
    if (reservation.getStatus() == ReservationStatus.DEADLINE_REFUND_AVAILABLE) {
      return reservation;
    }
    if (!reservation.getStatus().canTransitionTo(ReservationStatus.DEADLINE_REFUND_AVAILABLE)) {
      throw new BusinessException(
          ErrorCode.MARKETPLACE_RESERVATION_INVALID_STATUS,
          "Cannot claim deadline refund in status: " + reservation.getStatus());
    }
    return saveReservationPort.save(reservation.markDeadlineRefundAvailable());
  }

  private PrepareReservationEscrowCommand commandFor(Reservation reservation, Long buyerUserId) {
    String buyerWallet =
        walletOrSnapshot(reservation.getBuyerWalletAddress(), reservation.getUserId());
    String trainerWallet =
        walletOrSnapshot(reservation.getTrainerWalletAddress(), reservation.getTrainerId());
    var payment = loadReservationEscrowPaymentConfigPort.load();
    return new PrepareReservationEscrowCommand(
        reservation.getId(),
        reservation.getOrderId(),
        ensureOrderKey(reservation),
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
        reservation.getTokenAddress() == null
            ? payment.tokenAddress()
            : reservation.getTokenAddress(),
        reservation.getPriceBaseUnits() == null
            ? payment.priceBaseUnits(reservation.getBookedPriceAmount()).toString()
            : reservation.getPriceBaseUnits(),
        reservation.getBookedPriceAmount(),
        reservation.sessionEndAt(),
        reservation.getExpectedContractDeadlineEpochSeconds(),
        reservation.getContractDeadlineEpochSeconds(),
        reservation.getPendingAttemptToken(),
        "DEADLINE_REFUNDED");
  }

  private String walletOrSnapshot(String snapshot, Long userId) {
    if (snapshot != null && !snapshot.isBlank()) {
      return snapshot;
    }
    return loadReservationWalletPort
        .loadActiveWalletAddress(userId)
        .orElseThrow(
            () ->
                new BusinessException(
                    ErrorCode.WALLET_NOT_CONNECTED, "Active wallet not found: userId=" + userId));
  }

  private String ensureOrderKey(Reservation reservation) {
    if (reservation.getOrderKey() != null && !reservation.getOrderKey().isBlank()) {
      return reservation.getOrderKey();
    }
    try {
      UUID uuid = UUID.fromString(reservation.getOrderId());
      return "0x"
          + "0".repeat(32)
          + String.format(
              Locale.ROOT,
              "%016x%016x",
              uuid.getMostSignificantBits(),
              uuid.getLeastSignificantBits());
    } catch (RuntimeException e) {
      throw new BusinessException(
          ErrorCode.MARKETPLACE_DEADLINE_SYNC_REQUIRED,
          "Reservation order key is missing and cannot be derived");
    }
  }

  private record PendingPreparation(
      Reservation reservation, PrepareReservationEscrowCommand prepareCommand) {}

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
