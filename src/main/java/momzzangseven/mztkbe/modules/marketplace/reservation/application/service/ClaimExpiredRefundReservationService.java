package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import java.math.BigInteger;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;
import momzzangseven.mztkbe.global.error.marketplace.MarketplaceUnauthorizedAccessException;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ClaimExpiredRefundReservationCommand;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ClaimExpiredRefundReservationResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.PrepareReservationEscrowCommand;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.PrepareReservationEscrowResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.ClaimExpiredRefundReservationUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.CancelReservationEscrowExecutionPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationEscrowPaymentConfigPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationWalletPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.PrepareReservationEscrowExecutionPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SaveReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.Reservation;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

/** Prepares the buyer-owned claimExpiredRefund marketplace execution. */
@Slf4j
@Service
public class ClaimExpiredRefundReservationService implements ClaimExpiredRefundReservationUseCase {

  private final LoadReservationPort loadReservationPort;
  private final SaveReservationPort saveReservationPort;
  private final PrepareReservationEscrowExecutionPort prepareReservationEscrowExecutionPort;
  private final CancelReservationEscrowExecutionPort cancelReservationEscrowExecutionPort;
  private final LoadReservationWalletPort loadReservationWalletPort;
  private final LoadReservationEscrowPaymentConfigPort loadReservationEscrowPaymentConfigPort;
  private final Clock clock;
  private TransactionOperations transactionOperations;

  public ClaimExpiredRefundReservationService(
      LoadReservationPort loadReservationPort,
      SaveReservationPort saveReservationPort,
      @Nullable PrepareReservationEscrowExecutionPort prepareReservationEscrowExecutionPort,
      @Nullable CancelReservationEscrowExecutionPort cancelReservationEscrowExecutionPort,
      @Nullable LoadReservationWalletPort loadReservationWalletPort,
      @Nullable LoadReservationEscrowPaymentConfigPort loadReservationEscrowPaymentConfigPort,
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
    this.clock = clock;
  }

  @Autowired
  void setTransactionManager(PlatformTransactionManager transactionManager) {
    this.transactionOperations = new TransactionTemplate(transactionManager);
  }

  @Override
  public ClaimExpiredRefundReservationResult execute(ClaimExpiredRefundReservationCommand command) {
    PendingPreparation phaseA = runInTransaction(() -> beginDeadlineRefund(command));
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
    return new ClaimExpiredRefundReservationResult(
        saved.getId(), saved.getStatus(), saved.getEffectiveEscrowStatus().name(), prepared.web3());
  }

  private PendingPreparation beginDeadlineRefund(ClaimExpiredRefundReservationCommand command) {
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

    Reservation refundable = ensureRefundAvailable(reservation);
    Reservation pending =
        saveReservationPort.save(
            refundable.beginDeadlineRefundPending(UUID.randomUUID().toString()));
    return new PendingPreparation(pending, commandFor(pending, command.userId()));
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

  private Reservation ensureRefundAvailable(Reservation reservation) {
    if (reservation.getStatus() == ReservationStatus.DEADLINE_REFUND_PENDING) {
      throw new BusinessException(
          ErrorCode.MARKETPLACE_ACTIVE_EXECUTION_CONFLICT,
          "Deadline refund execution is already pending");
    }
    if (reservation.getStatus() == ReservationStatus.DEADLINE_REFUND_AVAILABLE) {
      return reservation;
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
            ? BigInteger.valueOf(reservation.getBookedPriceAmount()).toString()
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
}
