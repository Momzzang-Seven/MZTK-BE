package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import java.math.BigInteger;
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
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.PrepareReservationEscrowCommand;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.PrepareReservationEscrowResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.RecoverReservationEscrowCommand;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.RecoverReservationEscrowResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationEscrowOrderView;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationExecutionWriteView;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.RecoverReservationEscrowUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationEscrowOrderPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationEscrowPaymentConfigPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationExecutionWritePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationWalletPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.PrepareReservationEscrowExecutionPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.ReplayConfirmedReservationExecutionPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SaveReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.Reservation;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowAction;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Resumes or recreates user-owned marketplace execution intents for app re-entry. */
@Slf4j
@Service
public class RecoverReservationEscrowService implements RecoverReservationEscrowUseCase {

  private static final Set<String> RETRYABLE_TERMINAL_STATUSES =
      Set.of("FAILED_ONCHAIN", "EXPIRED", "CANCELED", "NONCE_STALE");

  private final LoadReservationPort loadReservationPort;
  private final SaveReservationPort saveReservationPort;
  private final PrepareReservationEscrowExecutionPort prepareReservationEscrowExecutionPort;
  private final LoadReservationExecutionWritePort loadReservationExecutionWritePort;
  private final ReplayConfirmedReservationExecutionPort replayConfirmedReservationExecutionPort;
  private final LoadReservationWalletPort loadReservationWalletPort;
  private final LoadReservationEscrowPaymentConfigPort loadReservationEscrowPaymentConfigPort;
  private final LoadReservationEscrowOrderPort loadReservationEscrowOrderPort;
  private final Clock clock;

  public RecoverReservationEscrowService(
      LoadReservationPort loadReservationPort,
      SaveReservationPort saveReservationPort,
      @Nullable PrepareReservationEscrowExecutionPort prepareReservationEscrowExecutionPort,
      @Nullable LoadReservationExecutionWritePort loadReservationExecutionWritePort,
      @Nullable ReplayConfirmedReservationExecutionPort replayConfirmedReservationExecutionPort,
      @Nullable LoadReservationWalletPort loadReservationWalletPort,
      @Nullable LoadReservationEscrowPaymentConfigPort loadReservationEscrowPaymentConfigPort,
      @Nullable LoadReservationEscrowOrderPort loadReservationEscrowOrderPort,
      Clock clock) {
    this.loadReservationPort = loadReservationPort;
    this.saveReservationPort = saveReservationPort;
    this.prepareReservationEscrowExecutionPort =
        prepareReservationEscrowExecutionPort == null
            ? DisabledReservationWeb3PortFactory.prepareExecution()
            : prepareReservationEscrowExecutionPort;
    this.loadReservationExecutionWritePort =
        loadReservationExecutionWritePort == null
            ? DisabledReservationWeb3PortFactory.executionWrite()
            : loadReservationExecutionWritePort;
    this.replayConfirmedReservationExecutionPort =
        replayConfirmedReservationExecutionPort == null
            ? DisabledReservationWeb3PortFactory.confirmedReplay()
            : replayConfirmedReservationExecutionPort;
    this.loadReservationWalletPort =
        loadReservationWalletPort == null
            ? DisabledReservationWeb3PortFactory.wallet()
            : loadReservationWalletPort;
    this.loadReservationEscrowPaymentConfigPort =
        loadReservationEscrowPaymentConfigPort == null
            ? DisabledReservationWeb3PortFactory.paymentConfig()
            : loadReservationEscrowPaymentConfigPort;
    this.loadReservationEscrowOrderPort =
        loadReservationEscrowOrderPort == null
            ? DisabledReservationWeb3PortFactory.escrowOrder()
            : loadReservationEscrowOrderPort;
    this.clock = clock;
  }

  @Override
  @Transactional
  public RecoverReservationEscrowResult execute(RecoverReservationEscrowCommand command) {
    Reservation reservation =
        loadReservationPort
            .findByIdWithLock(command.reservationId())
            .orElseThrow(
                () ->
                    new BusinessException(
                        ErrorCode.MARKETPLACE_RESERVATION_NOT_FOUND,
                        "Reservation not found: " + command.reservationId()));
    RecoveryFlow flow = resolveFlow(reservation);
    validateActor(reservation, command.requesterId(), flow);

    if (reservation.getCurrentExecutionIntentPublicId() != null) {
      ReservationExecutionWriteView current =
          loadReservationExecutionWritePort
              .load(command.requesterId(), reservation.getCurrentExecutionIntentPublicId())
              .asExistingForOrder(reservation.getOrderKey());
      String intentStatus = current.executionIntent().status();
      if ("CONFIRMED".equals(intentStatus)) {
        replayConfirmedReservationExecutionPort.replayConfirmed(
            current.executionIntent().id(), current.actionType());
        Reservation latest = loadReservationPort.findById(reservation.getId()).orElse(reservation);
        return result(latest, current);
      }
      if (!RETRYABLE_TERMINAL_STATUSES.contains(intentStatus)) {
        return result(reservation, current);
      }
    }

    ChainSyncResult chainSync = syncChainStateBeforePrepare(reservation, flow);
    if (chainSync.stop()) {
      return result(chainSync.reservation(), null);
    }
    reservation = chainSync.reservation();
    flow = resolveFlow(reservation);
    validateActor(reservation, command.requesterId(), flow);

    return prepareRecovery(command.requesterId(), reservation, flow);
  }

  private ChainSyncResult syncChainStateBeforePrepare(Reservation reservation, RecoveryFlow flow) {
    ReservationEscrowOrderView order;
    try {
      order = loadReservationEscrowOrderPort.getOrder(ensureOrderKey(reservation));
    } catch (MarketplaceWeb3DisabledException e) {
      return ChainSyncResult.continueWith(reservation);
    }
    if (order == null || order.isAbsent()) {
      if (flow.action() == RecoveryAction.PURCHASE) {
        return ChainSyncResult.continueWith(reservation);
      }
      throw new BusinessException(
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
          stopWith(
              reservation.syncChainOutcome(
                  cancelledStatus(reservation),
                  ReservationEscrowStatus.REFUNDED,
                  reservation.getTxHash(),
                  order.deadlineEpochSeconds(),
                  deadlineAt));
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
    return ChainSyncResult.continueWith(reservation);
  }

  private ChainSyncResult stopWith(Reservation reservation) {
    return ChainSyncResult.stop(saveReservationPort.save(reservation));
  }

  private ReservationStatus cancelledStatus(Reservation reservation) {
    return reservation.getPendingAction() == ReservationEscrowAction.TRAINER_REJECT
            || reservation.getStatus() == ReservationStatus.REJECT_PENDING
        ? ReservationStatus.REJECTED
        : ReservationStatus.USER_CANCELLED;
  }

  private LocalDateTime deadlineAt(Long epochSeconds) {
    if (epochSeconds == null) {
      return null;
    }
    return LocalDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), clock.getZone());
  }

  private RecoverReservationEscrowResult prepareRecovery(
      Long requesterId, Reservation reservation, RecoveryFlow flow) {
    Reservation current = ensurePreparedLocalState(reservation, flow);
    PrepareReservationEscrowResult prepared =
        switch (flow.action()) {
          case PURCHASE ->
              prepareReservationEscrowExecutionPort.preparePurchase(
                  commandFor(current, "BUYER", requesterId, current.getTrainerId(), "PENDING"));
          case BUYER_CANCEL ->
              prepareReservationEscrowExecutionPort.prepareCancel(
                  commandFor(
                      current, "BUYER", requesterId, current.getTrainerId(), "USER_CANCELLED"));
          case TRAINER_REJECT ->
              prepareReservationEscrowExecutionPort.prepareCancel(
                  commandFor(current, "TRAINER", requesterId, current.getUserId(), "REJECTED"));
          case BUYER_CONFIRM ->
              prepareReservationEscrowExecutionPort.prepareConfirm(
                  commandFor(current, "BUYER", requesterId, current.getTrainerId(), "SETTLED"));
          case DEADLINE_REFUND ->
              prepareReservationEscrowExecutionPort.prepareDeadlineRefund(
                  commandFor(
                      current, "BUYER", requesterId, current.getTrainerId(), "DEADLINE_REFUNDED"));
        };
    Reservation saved = saveReservationPort.save(bind(current, flow, prepared));
    log.info(
        "Reservation recovery intent prepared: id={}, requesterId={}, action={}, intentId={}",
        saved.getId(),
        requesterId,
        flow.action(),
        prepared.web3().executionIntent().id());
    return result(saved, prepared.web3());
  }

  private Reservation ensurePreparedLocalState(Reservation reservation, RecoveryFlow flow) {
    if (flow.action() != RecoveryAction.DEADLINE_REFUND) {
      return reservation;
    }
    if (reservation.getStatus() == ReservationStatus.DEADLINE_REFUND_PENDING) {
      return reservation;
    }
    if (reservation.getStatus() != ReservationStatus.DEADLINE_REFUND_AVAILABLE) {
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
      reservation = saveReservationPort.save(reservation.markDeadlineRefundAvailable());
    }
    return saveReservationPort.save(
        reservation.beginDeadlineRefundPending(UUID.randomUUID().toString()));
  }

  private Reservation bind(
      Reservation reservation, RecoveryFlow flow, PrepareReservationEscrowResult prepared) {
    String intentId = prepared.web3().executionIntent().id();
    if (flow.action() == RecoveryAction.PURCHASE
        && reservation.getStatus() == ReservationStatus.PURCHASE_PREPARING) {
      return reservation.bindPurchaseIntent(intentId);
    }
    if (flow.action() == RecoveryAction.PURCHASE) {
      return reservation.toBuilder().currentExecutionIntentPublicId(intentId).build();
    }
    return reservation.bindPendingExecutionIntent(intentId);
  }

  private RecoveryFlow resolveFlow(Reservation reservation) {
    return switch (reservation.getStatus()) {
      case PURCHASE_PREPARING, PURCHASE_PENDING -> new RecoveryFlow(RecoveryAction.PURCHASE, true);
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
          throw new BusinessException(
              ErrorCode.MARKETPLACE_DEADLINE_SYNC_REQUIRED,
              "Reservation requires deadline synchronization before recovery");
      default -> throwInvalidRecovery(reservation);
    };
  }

  private RecoveryFlow throwInvalidRecovery(Reservation reservation) {
    throw new BusinessException(
        ErrorCode.MARKETPLACE_RESERVATION_INVALID_STATUS,
        "Cannot recover reservation in status: " + reservation.getStatus());
  }

  private boolean expired(Reservation reservation) {
    return reservation.getContractDeadlineAt() != null
        && LocalDateTime.now(clock).isAfter(reservation.getContractDeadlineAt());
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

  private RecoverReservationEscrowResult result(
      Reservation reservation, ReservationExecutionWriteView web3) {
    return new RecoverReservationEscrowResult(
        reservation.getId(),
        reservation.getStatus(),
        reservation.getEffectiveEscrowStatus().name(),
        web3);
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
            ? BigInteger.valueOf(reservation.getBookedPriceAmount()).toString()
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
}
