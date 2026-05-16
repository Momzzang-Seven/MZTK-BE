package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import java.util.Locale;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;
import momzzangseven.mztkbe.global.error.marketplace.MarketplaceUnauthorizedAccessException;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.PrepareReservationEscrowCommand;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.PrepareReservationEscrowResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.RejectReservationCommand;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.RejectReservationResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationExecutionWriteView;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.RejectReservationUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.CancelReservationEscrowExecutionPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationEscrowPaymentConfigPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationWalletPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.PrepareReservationEscrowExecutionPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SaveReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.Reservation;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Service for a trainer to reject a PENDING reservation.
 *
 * <p><b>Two-phase ordering:</b><br>
 * The reservation first moves to a scheduler-invisible reject-pending state, then Web3 prepare runs
 * outside the row lock. Phase B relocks and binds the shared execution intent before returning the
 * sign request. REJECTED and the trainer strike are applied only by the confirmed execution hook.
 */
@Slf4j
@Service
public class RejectReservationService implements RejectReservationUseCase {

  private final LoadReservationPort loadReservationPort;
  private final SaveReservationPort saveReservationPort;
  private final PrepareReservationEscrowExecutionPort prepareReservationEscrowExecutionPort;
  private final CancelReservationEscrowExecutionPort cancelReservationEscrowExecutionPort;
  private final LoadReservationWalletPort loadReservationWalletPort;
  private final LoadReservationEscrowPaymentConfigPort loadReservationEscrowPaymentConfigPort;
  private TransactionOperations transactionOperations;

  @Autowired
  public RejectReservationService(
      LoadReservationPort loadReservationPort,
      SaveReservationPort saveReservationPort,
      @Nullable PrepareReservationEscrowExecutionPort prepareReservationEscrowExecutionPort,
      @Nullable CancelReservationEscrowExecutionPort cancelReservationEscrowExecutionPort,
      @Nullable LoadReservationWalletPort loadReservationWalletPort,
      @Nullable LoadReservationEscrowPaymentConfigPort loadReservationEscrowPaymentConfigPort) {
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
  }

  @Autowired
  void setTransactionManager(PlatformTransactionManager transactionManager) {
    this.transactionOperations = new TransactionTemplate(transactionManager);
  }

  public RejectReservationService(
      LoadReservationPort loadReservationPort,
      SaveReservationPort saveReservationPort,
      org.springframework.context.ApplicationEventPublisher ignored) {
    this(
        loadReservationPort,
        saveReservationPort,
        fakePreparePort(),
        DisabledReservationWeb3PortFactory.cancelExecution(),
        userId -> java.util.Optional.of("0x1111111111111111111111111111111111111111"),
        () ->
            new LoadReservationEscrowPaymentConfigPort.ReservationEscrowPaymentConfig(
                "0x3333333333333333333333333333333333333333", 18));
  }

  @Override
  public RejectReservationResult execute(RejectReservationCommand command) {
    log.debug(
        "RejectReservation: reservationId={}, trainerId={}",
        command.reservationId(),
        command.authenticatedTrainerId());

    PendingPreparation phaseA = runInTransaction(() -> beginReject(command));
    PrepareReservationEscrowResult prepared;
    try {
      prepared = prepareReservationEscrowExecutionPort.prepareCancel(phaseA.prepareCommand());
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
        saved.getId(), saved.getStatus(), saved.getEffectiveEscrowStatus().name(), prepared.web3());
  }

  private PendingPreparation beginReject(RejectReservationCommand command) {
    Reservation reservation =
        loadReservationPort
            .findByIdWithLock(command.reservationId())
            .orElseThrow(
                () ->
                    new BusinessException(
                        ErrorCode.MARKETPLACE_RESERVATION_NOT_FOUND,
                        "Reservation not found: " + command.reservationId()));

    if (!reservation.isOwnedByTrainer(command.authenticatedTrainerId())) {
      throw new MarketplaceUnauthorizedAccessException();
    }

    if (!reservation.getStatus().canTransitionTo(ReservationStatus.REJECTED)) {
      throw new BusinessException(
          ErrorCode.MARKETPLACE_RESERVATION_INVALID_STATUS,
          "Cannot reject reservation in status: " + reservation.getStatus());
    }
    validateUserEscrowLocked(reservation, "reject");

    Reservation pending =
        saveReservationPort.save(
            reservation.beginRejectPending(
                UUID.randomUUID().toString(), command.rejectionReason()));
    return new PendingPreparation(
        pending,
        commandFor(
            pending, "TRAINER", command.authenticatedTrainerId(), pending.getUserId(), "REJECTED"));
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
      throw new BusinessException(
          ErrorCode.MARKETPLACE_ACTIVE_EXECUTION_CONFLICT,
          "marketplace reservation state changed before execution intent bind");
    }
  }

  private void validateUserEscrowLocked(Reservation reservation, String action) {
    if (!reservation.getEffectiveEscrowFlow().isUserEip7702()
        || reservation.getEffectiveEscrowStatus() != ReservationEscrowStatus.LOCKED) {
      throw new BusinessException(
          ErrorCode.MARKETPLACE_RESERVATION_INVALID_STATUS,
          "Cannot "
              + action
              + " reservation before marketplace user escrow is confirmed and locked");
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

  private PrepareReservationEscrowCommand commandFor(
      Reservation reservation,
      String actorType,
      Long authorityUserId,
      Long counterpartyUserId,
      String targetTerminalStatus) {
    String buyerWallet =
        walletOrSnapshot(reservation.getBuyerWalletAddress(), reservation.getUserId());
    String trainerWallet =
        walletOrSnapshot(reservation.getTrainerWalletAddress(), reservation.getTrainerId());
    var payment = loadReservationEscrowPaymentConfigPort.load();
    return new PrepareReservationEscrowCommand(
        reservation.getId(),
        reservation.getOrderId(),
        ensureOrderKey(reservation),
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
        targetTerminalStatus);
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

  private static PrepareReservationEscrowExecutionPort fakePreparePort() {
    return new PrepareReservationEscrowExecutionPort() {
      @Override
      public PrepareReservationEscrowResult preparePurchase(
          PrepareReservationEscrowCommand command) {
        return fakeResult();
      }

      @Override
      public PrepareReservationEscrowResult prepareCancel(PrepareReservationEscrowCommand command) {
        return fakeResult();
      }

      @Override
      public PrepareReservationEscrowResult prepareConfirm(
          PrepareReservationEscrowCommand command) {
        return fakeResult();
      }

      @Override
      public PrepareReservationEscrowResult prepareDeadlineRefund(
          PrepareReservationEscrowCommand command) {
        return fakeResult();
      }
    };
  }

  private static PrepareReservationEscrowResult fakeResult() {
    return new PrepareReservationEscrowResult(
        new ReservationExecutionWriteView(
            new ReservationExecutionWriteView.Resource("ORDER", "1", "PENDING_EXECUTION"),
            "MARKETPLACE_CLASS_CANCEL",
            "0x" + "0".repeat(63) + "1",
            new ReservationExecutionWriteView.ExecutionIntent(
                "intent-1",
                "AWAITING_SIGNATURE",
                java.time.LocalDateTime.now().plusMinutes(5),
                300L),
            new ReservationExecutionWriteView.Execution("EIP7702", 1),
            null,
            null,
            false,
            null,
            null));
  }

  private record PendingPreparation(
      Reservation reservation, PrepareReservationEscrowCommand prepareCommand) {}
}
