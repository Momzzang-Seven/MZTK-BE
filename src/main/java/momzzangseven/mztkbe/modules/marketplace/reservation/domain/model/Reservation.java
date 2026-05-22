package momzzangseven.mztkbe.modules.marketplace.reservation.domain.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import momzzangseven.mztkbe.global.error.ErrorCode;
import momzzangseven.mztkbe.global.error.marketplace.MarketplaceReservationStateException;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowAction;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowFlow;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationTerminalResolvedBy;

/**
 * Aggregate root representing a single class reservation.
 *
 * <p>All fields are {@code private final} to enforce immutability. State transitions are expressed
 * as methods that return a new instance via {@code toBuilder()} — never mutate in place.
 *
 * <h2>Denormalised fields</h2>
 *
 * <ul>
 *   <li>{@code trainerId} — copied from MarketplaceClass at creation for fast ownership checks.
 *   <li>{@code durationMinutes} — copied from ClassSlot so the auto-settle scheduler can compute
 *       session end-time without a cross-module query.
 * </ul>
 */
@Getter
@Builder(toBuilder = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Reservation {

  private final Long id;

  /** User who made the reservation. */
  private final Long userId;

  /**
   * Trainer who owns the class. Denormalised from MarketplaceClass at creation time to avoid a
   * cross-module lookup during approve/reject operations.
   */
  private final Long trainerId;

  /** The class slot this reservation targets. */
  private final Long slotId;

  /** Actual date of the session. */
  private final LocalDate reservationDate;

  /** Start time of the session. */
  private final LocalTime reservationTime;

  /**
   * Session duration in minutes. Denormalised from ClassSlot so auto-settlement can compute class
   * end-time ({@code reservationDate + reservationTime + durationMinutes}) without an extra join.
   */
  private final int durationMinutes;

  /** Current lifecycle status of this reservation. */
  private final ReservationStatus status;

  /** User-managed on-chain escrow substate. Null means legacy row before MOM-313 backfill. */
  private final ReservationEscrowStatus escrowStatus;

  /** Execution model that owns this row. Null is treated as legacy dispatch for old rows. */
  private final ReservationEscrowFlow escrowFlow;

  /** Optional note from the user (max 500 chars). */
  private final String userRequest;

  /** Optional reason from the trainer when rejecting the reservation. */
  private final String rejectionReason;

  /** Server-generated UUID used as the on-chain order identifier in the escrow contract. */
  private final String orderId;

  /** Canonical Solidity order key: lowercase 0x + 64 hex chars. */
  private final String orderKey;

  /** Shared execution public id currently bound to this reservation. */
  private final String currentExecutionIntentPublicId;

  private final String buyerWalletAddress;
  private final String trainerWalletAddress;
  private final String tokenAddress;
  private final String priceBaseUnits;

  /** Most recent on-chain transaction hash associated with this reservation. */
  private final String txHash;

  private final LocalDateTime holdExpiresAt;
  private final LocalDateTime pendingActionExpiresAt;
  private final Long expectedContractDeadlineEpochSeconds;
  private final LocalDateTime expectedContractDeadlineAt;
  private final Long contractDeadlineEpochSeconds;
  private final LocalDateTime contractDeadlineAt;
  private final ReservationEscrowAction pendingAction;
  private final String pendingAttemptToken;
  private final Long pendingExpectedVersion;
  private final ReservationStatus pendingExpectedStatus;
  private final ReservationEscrowStatus pendingExpectedEscrowStatus;
  private final ReservationStatus priorStatus;
  private final ReservationEscrowStatus priorEscrowStatus;
  private final String createIdempotencyKeyHash;
  private final String createPayloadHash;
  private final LocalDateTime serverSignatureSignedAt;
  private final LocalDateTime serverSignatureExpiresAt;
  private final String escrowFailureCode;
  private final String escrowFailureMessage;
  private final ReservationTerminalResolvedBy resolvedBy;
  private final String terminalReasonCode;

  /**
   * Class price in KRW at the moment of booking. Denormalised snapshot so that past reservations
   * always display the price the user actually paid, even if the trainer later changes the class
   * price.
   *
   * <p>{@code null} for legacy records created before this snapshot column was added (pre-V065).
   * The service layer treats {@code null} as a signal to fall back to a live cross-module lookup.
   */
  private final Integer bookedPriceAmount;

  /**
   * Class title at the moment of booking. Denormalised snapshot so that past reservations display
   * the title as it was when the user booked, even if the trainer later renames the class.
   */
  private final String bookedClassTitle;

  /** JPA optimistic-lock version. Null for unsaved instances. */
  private final Long version;

  private final LocalDateTime createdAt;
  private final LocalDateTime updatedAt;

  // ============================================================
  // Factory
  // ============================================================

  /**
   * Create a new reservation in the {@link ReservationStatus#PENDING} state.
   *
   * @param userId the reserving user's ID
   * @param trainerId trainer's ID (denormalised from the class)
   * @param slotId target time-slot ID
   * @param reservationDate scheduled session date
   * @param reservationTime session start time
   * @param durationMinutes session duration (denormalised from slot)
   * @param userRequest optional user note (nullable)
   * @param orderId server-generated UUID for the escrow contract
   * @param txHash on-chain transaction hash returned by the escrow submit
   * @return new PENDING reservation
   */
  public static Reservation createPending(
      Long userId,
      Long trainerId,
      Long slotId,
      LocalDate reservationDate,
      LocalTime reservationTime,
      int durationMinutes,
      String userRequest,
      String orderId,
      String txHash,
      Integer bookedPriceAmount,
      String bookedClassTitle) {

    return Reservation.builder()
        .userId(userId)
        .trainerId(trainerId)
        .slotId(slotId)
        .reservationDate(reservationDate)
        .reservationTime(reservationTime)
        .durationMinutes(durationMinutes)
        .status(ReservationStatus.PENDING)
        .escrowStatus(ReservationEscrowStatus.NONE)
        .escrowFlow(ReservationEscrowFlow.LEGACY_DISPATCH)
        .userRequest(userRequest)
        .rejectionReason(null)
        .orderId(orderId)
        .txHash(txHash)
        .bookedPriceAmount(bookedPriceAmount)
        .bookedClassTitle(bookedClassTitle)
        .build();
  }

  // ============================================================
  // State-transition methods
  // ============================================================

  /**
   * Approve this reservation (PENDING → APPROVED).
   *
   * @return new APPROVED reservation
   * @throws BusinessException when current status does not allow APPROVED transition
   */
  public Reservation approve() {
    guardTransition(ReservationStatus.APPROVED);
    return toBuilder().status(ReservationStatus.APPROVED).build();
  }

  /**
   * Cancel this reservation by the user (PENDING → USER_CANCELLED).
   *
   * @param cancelTxHash on-chain transaction hash of the cancelClass call
   * @return new USER_CANCELLED reservation
   */
  public Reservation cancelByUser(String cancelTxHash) {
    guardTransition(ReservationStatus.USER_CANCELLED);
    return clearPendingExecutionState(
            toBuilder()
                .status(ReservationStatus.USER_CANCELLED)
                .escrowStatus(ReservationEscrowStatus.REFUNDED)
                .txHash(cancelTxHash))
        .build();
  }

  /**
   * Reject this reservation by the trainer (PENDING → REJECTED).
   *
   * @param rejectionTxHash on-chain transaction hash of the cancelClass call
   * @param rejectionReason reason provided by the trainer (nullable)
   * @return new REJECTED reservation
   */
  public Reservation reject(String rejectionTxHash, String rejectionReason) {
    guardTransition(ReservationStatus.REJECTED);
    return clearPendingExecutionState(
            toBuilder()
                .status(ReservationStatus.REJECTED)
                .escrowStatus(ReservationEscrowStatus.REFUNDED)
                .txHash(rejectionTxHash)
                .rejectionReason(rejectionReason))
        .build();
  }

  /**
   * Reject this reservation without a reason (PENDING → REJECTED).
   *
   * @param rejectionTxHash on-chain transaction hash of the cancelClass call
   * @return new REJECTED reservation with {@code rejectionReason = null}
   */
  public Reservation reject(String rejectionTxHash) {
    return reject(rejectionTxHash, null);
  }

  /**
   * Mark as settled by the user after class completion (APPROVED → SETTLED).
   *
   * @param confirmTxHash on-chain transaction hash of the confirmClass call
   * @return new SETTLED reservation
   */
  public Reservation complete(String confirmTxHash) {
    guardTransition(ReservationStatus.SETTLED);
    return clearPendingExecutionState(
            toBuilder()
                .status(ReservationStatus.SETTLED)
                .escrowStatus(ReservationEscrowStatus.SETTLED)
                .txHash(confirmTxHash))
        .build();
  }

  /**
   * Mark as auto-cancelled due to trainer inactivity (PENDING → TIMEOUT_CANCELLED).
   *
   * @param refundTxHash on-chain transaction hash of the adminRefund call
   * @return new TIMEOUT_CANCELLED reservation
   */
  public Reservation timeoutCancel(String refundTxHash) {
    guardTransition(ReservationStatus.TIMEOUT_CANCELLED);
    return clearPendingExecutionState(
            toBuilder()
                .status(ReservationStatus.TIMEOUT_CANCELLED)
                .escrowStatus(ReservationEscrowStatus.REFUNDED)
                .txHash(refundTxHash))
        .build();
  }

  /**
   * Mark as auto-settled by the scheduler (APPROVED → AUTO_SETTLED).
   *
   * @param settleTxHash on-chain transaction hash of the adminSettle call
   * @return new AUTO_SETTLED reservation
   */
  public Reservation autoSettle(String settleTxHash) {
    guardTransition(ReservationStatus.AUTO_SETTLED);
    return clearPendingExecutionState(
            toBuilder()
                .status(ReservationStatus.AUTO_SETTLED)
                .escrowStatus(ReservationEscrowStatus.SETTLED)
                .txHash(settleTxHash))
        .build();
  }

  // ============================================================
  // Domain query helpers
  // ============================================================

  /** Returns true when this reservation is owned by the given user. */
  public boolean isOwnedByUser(Long targetUserId) {
    return userId != null && userId.equals(targetUserId);
  }

  /** Returns true when this reservation belongs to the given trainer. */
  public boolean isOwnedByTrainer(Long targetTrainerId) {
    return trainerId != null && trainerId.equals(targetTrainerId);
  }

  /** Computes the session end {@link LocalDateTime} using the denormalised duration. */
  public LocalDateTime sessionEndAt() {
    return LocalDateTime.of(reservationDate, reservationTime).plusMinutes(durationMinutes);
  }

  /**
   * Return a new instance with only the {@code txHash} field updated.
   *
   * <p>Used by the {@code EscrowDispatchEventListener} to write back the real on-chain transaction
   * hash after the escrow call succeeds post-commit. All other fields remain unchanged.
   *
   * @param newTxHash the real txHash returned by the escrow contract
   * @return new reservation instance with updated txHash
   */
  public Reservation updateTxHash(String newTxHash) {
    return toBuilder().txHash(newTxHash).build();
  }

  public Reservation beginPurchasePreparing(
      String createIdempotencyKeyHash, String createPayloadHash, LocalDateTime holdExpiresAt) {
    return beginPurchasePreparing(
        createIdempotencyKeyHash,
        createPayloadHash,
        holdExpiresAt,
        orderKey,
        buyerWalletAddress,
        trainerWalletAddress,
        tokenAddress,
        priceBaseUnits,
        expectedContractDeadlineEpochSeconds,
        expectedContractDeadlineAt,
        pendingAttemptToken);
  }

  public Reservation beginPurchasePreparing(
      String createIdempotencyKeyHash,
      String createPayloadHash,
      LocalDateTime holdExpiresAt,
      String orderKey,
      String buyerWalletAddress,
      String trainerWalletAddress,
      String tokenAddress,
      String priceBaseUnits,
      Long expectedContractDeadlineEpochSeconds,
      LocalDateTime expectedContractDeadlineAt) {
    return beginPurchasePreparing(
        createIdempotencyKeyHash,
        createPayloadHash,
        holdExpiresAt,
        orderKey,
        buyerWalletAddress,
        trainerWalletAddress,
        tokenAddress,
        priceBaseUnits,
        expectedContractDeadlineEpochSeconds,
        expectedContractDeadlineAt,
        pendingAttemptToken);
  }

  public Reservation beginPurchasePreparing(
      String createIdempotencyKeyHash,
      String createPayloadHash,
      LocalDateTime holdExpiresAt,
      String orderKey,
      String buyerWalletAddress,
      String trainerWalletAddress,
      String tokenAddress,
      String priceBaseUnits,
      Long expectedContractDeadlineEpochSeconds,
      LocalDateTime expectedContractDeadlineAt,
      String pendingAttemptToken) {
    guardTransition(ReservationStatus.HOLDING);
    return toBuilder()
        .status(ReservationStatus.HOLDING)
        .escrowStatus(ReservationEscrowStatus.PURCHASE_PREPARING)
        .escrowFlow(ReservationEscrowFlow.USER_EIP7702)
        .orderKey(orderKey)
        .buyerWalletAddress(buyerWalletAddress)
        .trainerWalletAddress(trainerWalletAddress)
        .tokenAddress(tokenAddress)
        .priceBaseUnits(priceBaseUnits)
        .expectedContractDeadlineEpochSeconds(expectedContractDeadlineEpochSeconds)
        .expectedContractDeadlineAt(expectedContractDeadlineAt)
        .pendingAction(ReservationEscrowAction.PURCHASE)
        .pendingAttemptToken(pendingAttemptToken)
        .holdExpiresAt(holdExpiresAt)
        .createIdempotencyKeyHash(createIdempotencyKeyHash)
        .createPayloadHash(createPayloadHash)
        .build();
  }

  public Reservation bindPendingExecutionIntent(String executionIntentPublicId) {
    ReservationEscrowStatus nextEscrowStatus =
        switch (status) {
          case CANCEL_PENDING -> ReservationEscrowStatus.CANCEL_PENDING;
          case REJECT_PENDING -> ReservationEscrowStatus.REJECT_PENDING;
          case CONFIRM_PENDING -> ReservationEscrowStatus.CONFIRM_PENDING;
          case DEADLINE_REFUND_PENDING -> ReservationEscrowStatus.DEADLINE_REFUND_PENDING;
          default ->
              throw new MarketplaceReservationStateException(
                  ErrorCode.MARKETPLACE_RESERVATION_INVALID_STATUS,
                  "Cannot bind execution intent from " + status);
        };
    return toBuilder()
        .currentExecutionIntentPublicId(executionIntentPublicId)
        .escrowStatus(nextEscrowStatus)
        .build();
  }

  public Reservation bindAdminPendingExecutionIntent(String executionIntentPublicId) {
    ReservationEscrowStatus nextEscrowStatus =
        switch (status) {
          case ADMIN_REFUND_PENDING -> ReservationEscrowStatus.ADMIN_REFUND_PENDING;
          case ADMIN_SETTLE_PENDING -> ReservationEscrowStatus.ADMIN_SETTLE_PENDING;
          default ->
              throw new MarketplaceReservationStateException(
                  ErrorCode.MARKETPLACE_RESERVATION_INVALID_STATUS,
                  "Cannot bind admin execution intent from " + status);
        };
    return toBuilder()
        .currentExecutionIntentPublicId(executionIntentPublicId)
        .escrowStatus(nextEscrowStatus)
        .build();
  }

  public Reservation bindPurchaseIntent(String executionIntentPublicId) {
    if (status != ReservationStatus.HOLDING && status != ReservationStatus.PURCHASE_PREPARING) {
      throw new MarketplaceReservationStateException(
          ErrorCode.MARKETPLACE_RESERVATION_INVALID_STATUS,
          "Cannot bind purchase intent from " + status);
    }
    return toBuilder()
        .status(ReservationStatus.HOLDING)
        .escrowStatus(ReservationEscrowStatus.PURCHASE_PENDING)
        .currentExecutionIntentPublicId(executionIntentPublicId)
        .build();
  }

  public Reservation retryPurchasePreparing(
      String pendingAttemptToken, LocalDateTime holdExpiresAt) {
    boolean retryablePreparationFailure =
        status == ReservationStatus.HOLDING
            && getEffectiveEscrowStatus() == ReservationEscrowStatus.PURCHASE_PREPARING
            && currentExecutionIntentPublicId == null;
    boolean retryableTerminalIntent =
        status == ReservationStatus.HOLDING
            && getEffectiveEscrowStatus() == ReservationEscrowStatus.PURCHASE_PENDING
            && currentExecutionIntentPublicId != null;
    if (!retryablePreparationFailure && !retryableTerminalIntent) {
      throw new MarketplaceReservationStateException(
          ErrorCode.MARKETPLACE_RESERVATION_INVALID_STATUS,
          "Cannot retry purchase preparation from " + status);
    }
    return toBuilder()
        .status(ReservationStatus.HOLDING)
        .escrowStatus(ReservationEscrowStatus.PURCHASE_PREPARING)
        .currentExecutionIntentPublicId(null)
        .pendingAction(ReservationEscrowAction.PURCHASE)
        .pendingAttemptToken(pendingAttemptToken)
        .holdExpiresAt(holdExpiresAt)
        .build();
  }

  public Reservation markPurchaseConfirmedLocked(
      Long contractDeadlineEpochSeconds, LocalDateTime contractDeadlineAt) {
    return markPurchaseConfirmedLocked(contractDeadlineEpochSeconds, contractDeadlineAt, null);
  }

  public Reservation markPurchaseConfirmedLocked(
      Long contractDeadlineEpochSeconds, LocalDateTime contractDeadlineAt, String txHash) {
    guardTransition(ReservationStatus.PENDING);
    return toBuilder()
        .status(ReservationStatus.PENDING)
        .escrowStatus(ReservationEscrowStatus.LOCKED)
        .txHash(txHash)
        .contractDeadlineEpochSeconds(contractDeadlineEpochSeconds)
        .contractDeadlineAt(contractDeadlineAt)
        .currentExecutionIntentPublicId(null)
        .pendingAction(null)
        .pendingAttemptToken(null)
        .pendingActionExpiresAt(null)
        .pendingExpectedVersion(null)
        .pendingExpectedStatus(null)
        .pendingExpectedEscrowStatus(null)
        .priorStatus(null)
        .priorEscrowStatus(null)
        .build();
  }

  public Reservation markPurchaseDeadlineRecoveryRequired(
      Long contractDeadlineEpochSeconds, LocalDateTime contractDeadlineAt) {
    return markPurchaseDeadlineRecoveryRequired(
        contractDeadlineEpochSeconds, contractDeadlineAt, null);
  }

  public Reservation markPurchaseDeadlineRecoveryRequired(
      Long contractDeadlineEpochSeconds, LocalDateTime contractDeadlineAt, String txHash) {
    guardTransition(ReservationStatus.DEADLINE_RECOVERY_REQUIRED);
    return toBuilder()
        .status(ReservationStatus.DEADLINE_RECOVERY_REQUIRED)
        .escrowStatus(ReservationEscrowStatus.DEADLINE_RECOVERY_REQUIRED)
        .txHash(txHash)
        .contractDeadlineEpochSeconds(contractDeadlineEpochSeconds)
        .contractDeadlineAt(contractDeadlineAt)
        .currentExecutionIntentPublicId(null)
        .pendingAction(null)
        .pendingAttemptToken(null)
        .pendingActionExpiresAt(null)
        .pendingExpectedVersion(null)
        .pendingExpectedStatus(null)
        .pendingExpectedEscrowStatus(null)
        .priorStatus(null)
        .priorEscrowStatus(null)
        .build();
  }

  public Reservation repairCreatedChainOrder(
      Long contractDeadlineEpochSeconds,
      LocalDateTime contractDeadlineAt,
      boolean completionWindowFits) {
    if (!isCreatedChainRepairCandidate()) {
      throw new MarketplaceReservationStateException(
          ErrorCode.MARKETPLACE_RESERVATION_INVALID_STATUS,
          "Cannot repair created chain order from " + status);
    }
    ReservationStatus nextStatus;
    ReservationEscrowStatus nextEscrowStatus;
    if (completionWindowFits) {
      nextStatus =
          status == ReservationStatus.APPROVED
              ? ReservationStatus.APPROVED
              : ReservationStatus.PENDING;
      nextEscrowStatus = ReservationEscrowStatus.LOCKED;
    } else {
      nextStatus = ReservationStatus.DEADLINE_RECOVERY_REQUIRED;
      nextEscrowStatus = ReservationEscrowStatus.DEADLINE_RECOVERY_REQUIRED;
    }
    return toBuilder()
        .status(nextStatus)
        .escrowStatus(nextEscrowStatus)
        .contractDeadlineEpochSeconds(contractDeadlineEpochSeconds)
        .contractDeadlineAt(contractDeadlineAt)
        .currentExecutionIntentPublicId(null)
        .pendingAction(null)
        .pendingAttemptToken(null)
        .pendingExpectedVersion(null)
        .pendingExpectedStatus(null)
        .pendingExpectedEscrowStatus(null)
        .priorStatus(null)
        .priorEscrowStatus(null)
        .build();
  }

  private boolean isCreatedChainRepairCandidate() {
    return switch (status) {
      case PURCHASE_PREPARING,
              PURCHASE_PENDING,
              PENDING,
              APPROVED,
              DEADLINE_RECOVERY_REQUIRED,
              DEADLINE_SYNC_REQUIRED ->
          true;
      default -> false;
    };
  }

  public Reservation markDeadlineSyncRequired(String failureCode, String failureMessage) {
    guardTransition(ReservationStatus.DEADLINE_SYNC_REQUIRED);
    return toBuilder()
        .status(ReservationStatus.DEADLINE_SYNC_REQUIRED)
        .escrowStatus(ReservationEscrowStatus.DEADLINE_SYNC_REQUIRED)
        .escrowFailureCode(failureCode)
        .escrowFailureMessage(failureMessage)
        .currentExecutionIntentPublicId(null)
        .pendingAction(null)
        .pendingAttemptToken(null)
        .pendingActionExpiresAt(null)
        .pendingExpectedVersion(null)
        .pendingExpectedStatus(null)
        .pendingExpectedEscrowStatus(null)
        .priorStatus(null)
        .priorEscrowStatus(null)
        .build();
  }

  public Reservation syncChainOutcome(
      ReservationStatus outcomeStatus,
      ReservationEscrowStatus outcomeEscrowStatus,
      String txHash,
      Long contractDeadlineEpochSeconds,
      LocalDateTime contractDeadlineAt) {
    return syncChainOutcome(
        outcomeStatus,
        outcomeEscrowStatus,
        txHash,
        contractDeadlineEpochSeconds,
        contractDeadlineAt,
        null,
        null);
  }

  public Reservation syncChainOutcome(
      ReservationStatus outcomeStatus,
      ReservationEscrowStatus outcomeEscrowStatus,
      String txHash,
      Long contractDeadlineEpochSeconds,
      LocalDateTime contractDeadlineAt,
      ReservationTerminalResolvedBy resolvedBy,
      String terminalReasonCode) {
    if (!outcomeStatus.isTerminal() && outcomeStatus != ReservationStatus.MANUAL_SYNC_REQUIRED) {
      throw new MarketplaceReservationStateException(
          ErrorCode.MARKETPLACE_RESERVATION_INVALID_STATUS,
          "Unsupported chain outcome status: " + outcomeStatus);
    }
    return toBuilder()
        .status(outcomeStatus)
        .escrowStatus(outcomeEscrowStatus)
        .txHash(txHash)
        .contractDeadlineEpochSeconds(contractDeadlineEpochSeconds)
        .contractDeadlineAt(contractDeadlineAt)
        .resolvedBy(resolvedBy)
        .terminalReasonCode(terminalReasonCode)
        .currentExecutionIntentPublicId(null)
        .pendingAction(null)
        .pendingAttemptToken(null)
        .pendingActionExpiresAt(null)
        .pendingExpectedVersion(null)
        .pendingExpectedStatus(null)
        .pendingExpectedEscrowStatus(null)
        .priorStatus(null)
        .priorEscrowStatus(null)
        .build();
  }

  public Reservation markDeadlineRefundAvailable() {
    guardTransition(ReservationStatus.DEADLINE_REFUND_AVAILABLE);
    return clearPendingExecutionState(
            toBuilder()
                .status(ReservationStatus.DEADLINE_REFUND_AVAILABLE)
                .escrowStatus(ReservationEscrowStatus.DEADLINE_REFUND_AVAILABLE))
        .build();
  }

  public Reservation markDeadlineRefundAvailable(
      Long contractDeadlineEpochSeconds, LocalDateTime contractDeadlineAt) {
    return markDeadlineRefundAvailable().toBuilder()
        .contractDeadlineEpochSeconds(contractDeadlineEpochSeconds)
        .contractDeadlineAt(contractDeadlineAt)
        .build();
  }

  public Reservation beginDeadlineRefundPending(String pendingAttemptToken) {
    guardTransition(ReservationStatus.DEADLINE_REFUND_PENDING);
    return toBuilder()
        .status(ReservationStatus.DEADLINE_REFUND_PENDING)
        .escrowStatus(ReservationEscrowStatus.DEADLINE_REFUND_PENDING)
        .pendingAction(ReservationEscrowAction.DEADLINE_REFUND)
        .pendingAttemptToken(pendingAttemptToken)
        .priorStatus(ReservationStatus.DEADLINE_REFUND_AVAILABLE)
        .priorEscrowStatus(getEffectiveEscrowStatus())
        .build();
  }

  public Reservation beginAdminRefundPending(
      String pendingAttemptToken, LocalDateTime pendingActionExpiresAt) {
    if (getEffectiveEscrowStatus() != ReservationEscrowStatus.LOCKED) {
      throw new MarketplaceReservationStateException(
          ErrorCode.MARKETPLACE_RESERVATION_INVALID_STATUS, "Admin refund requires locked escrow");
    }
    guardTransition(ReservationStatus.ADMIN_REFUND_PENDING);
    return toBuilder()
        .status(ReservationStatus.ADMIN_REFUND_PENDING)
        .escrowStatus(ReservationEscrowStatus.ADMIN_REFUND_PENDING)
        .pendingAction(ReservationEscrowAction.ADMIN_REFUND)
        .pendingAttemptToken(pendingAttemptToken)
        .pendingActionExpiresAt(pendingActionExpiresAt)
        .pendingExpectedStatus(ReservationStatus.ADMIN_REFUND_PENDING)
        .pendingExpectedEscrowStatus(ReservationEscrowStatus.ADMIN_REFUND_PENDING)
        .priorStatus(status)
        .priorEscrowStatus(getEffectiveEscrowStatus())
        .build();
  }

  public Reservation beginAdminSettlePending(
      String pendingAttemptToken, LocalDateTime pendingActionExpiresAt) {
    if (getEffectiveEscrowStatus() != ReservationEscrowStatus.LOCKED) {
      throw new MarketplaceReservationStateException(
          ErrorCode.MARKETPLACE_RESERVATION_INVALID_STATUS,
          "Admin settlement requires locked escrow");
    }
    guardTransition(ReservationStatus.ADMIN_SETTLE_PENDING);
    return toBuilder()
        .status(ReservationStatus.ADMIN_SETTLE_PENDING)
        .escrowStatus(ReservationEscrowStatus.ADMIN_SETTLE_PENDING)
        .pendingAction(ReservationEscrowAction.ADMIN_SETTLE)
        .pendingAttemptToken(pendingAttemptToken)
        .pendingActionExpiresAt(pendingActionExpiresAt)
        .pendingExpectedStatus(ReservationStatus.ADMIN_SETTLE_PENDING)
        .pendingExpectedEscrowStatus(ReservationEscrowStatus.ADMIN_SETTLE_PENDING)
        .priorStatus(status)
        .priorEscrowStatus(getEffectiveEscrowStatus())
        .build();
  }

  public Reservation markAdminRefunded(
      String txHash, ReservationTerminalResolvedBy resolvedBy, String terminalReasonCode) {
    guardTransition(ReservationStatus.TIMEOUT_CANCELLED);
    return clearPendingExecutionState(
            toBuilder()
                .status(ReservationStatus.TIMEOUT_CANCELLED)
                .escrowStatus(ReservationEscrowStatus.REFUNDED)
                .txHash(txHash)
                .resolvedBy(resolvedBy)
                .terminalReasonCode(terminalReasonCode))
        .build();
  }

  public Reservation markAdminSettled(
      String txHash, ReservationTerminalResolvedBy resolvedBy, String terminalReasonCode) {
    guardTransition(ReservationStatus.AUTO_SETTLED);
    return clearPendingExecutionState(
            toBuilder()
                .status(ReservationStatus.AUTO_SETTLED)
                .escrowStatus(ReservationEscrowStatus.SETTLED)
                .txHash(txHash)
                .resolvedBy(resolvedBy)
                .terminalReasonCode(terminalReasonCode))
        .build();
  }

  public Reservation markDeadlineRefunded(String txHash) {
    guardTransition(ReservationStatus.DEADLINE_REFUNDED);
    return clearPendingExecutionState(
            toBuilder()
                .status(ReservationStatus.DEADLINE_REFUNDED)
                .escrowStatus(ReservationEscrowStatus.DEADLINE_REFUNDED)
                .txHash(txHash))
        .build();
  }

  public Reservation markHoldExpired() {
    guardTransition(ReservationStatus.HOLD_EXPIRED);
    return clearPendingExecutionState(
            toBuilder()
                .status(ReservationStatus.HOLD_EXPIRED)
                .escrowStatus(ReservationEscrowStatus.HOLD_EXPIRED))
        .build();
  }

  public Reservation markPaymentFailed(String failureCode, String failureMessage) {
    guardTransition(ReservationStatus.PAYMENT_FAILED);
    return clearPendingExecutionState(
            toBuilder()
                .status(ReservationStatus.PAYMENT_FAILED)
                .escrowStatus(ReservationEscrowStatus.PAYMENT_FAILED)
                .escrowFailureCode(failureCode)
                .escrowFailureMessage(failureMessage))
        .build();
  }

  public Reservation beginCancelPending(String pendingAttemptToken) {
    guardTransition(ReservationStatus.CANCEL_PENDING);
    return toBuilder()
        .status(ReservationStatus.CANCEL_PENDING)
        .escrowStatus(ReservationEscrowStatus.CANCEL_PENDING)
        .pendingAction(ReservationEscrowAction.BUYER_CANCEL)
        .pendingAttemptToken(pendingAttemptToken)
        .priorStatus(ReservationStatus.PENDING)
        .priorEscrowStatus(getEffectiveEscrowStatus())
        .build();
  }

  public Reservation beginRejectPending(String pendingAttemptToken) {
    return beginRejectPending(pendingAttemptToken, null);
  }

  public Reservation beginRejectPending(String pendingAttemptToken, String rejectionReason) {
    guardTransition(ReservationStatus.REJECT_PENDING);
    return toBuilder()
        .status(ReservationStatus.REJECT_PENDING)
        .escrowStatus(ReservationEscrowStatus.REJECT_PENDING)
        .rejectionReason(rejectionReason)
        .pendingAction(ReservationEscrowAction.TRAINER_REJECT)
        .pendingAttemptToken(pendingAttemptToken)
        .priorStatus(ReservationStatus.PENDING)
        .priorEscrowStatus(getEffectiveEscrowStatus())
        .build();
  }

  public Reservation beginConfirmPending(String pendingAttemptToken) {
    guardTransition(ReservationStatus.CONFIRM_PENDING);
    return toBuilder()
        .status(ReservationStatus.CONFIRM_PENDING)
        .escrowStatus(ReservationEscrowStatus.CONFIRM_PENDING)
        .pendingAction(ReservationEscrowAction.BUYER_CONFIRM)
        .pendingAttemptToken(pendingAttemptToken)
        .priorStatus(ReservationStatus.APPROVED)
        .priorEscrowStatus(getEffectiveEscrowStatus())
        .build();
  }

  public Reservation rollbackToPriorState() {
    if (priorStatus == null) {
      throw new MarketplaceReservationStateException(
          ErrorCode.MARKETPLACE_RESERVATION_INVALID_STATUS,
          "Prior status is not available for rollback from " + status);
    }
    guardTransition(priorStatus);
    ReservationBuilder builder =
        toBuilder()
            .status(priorStatus)
            .escrowStatus(priorEscrowStatus)
            .currentExecutionIntentPublicId(null)
            .pendingAction(null)
            .pendingAttemptToken(null)
            .pendingActionExpiresAt(null)
            .pendingExpectedVersion(null)
            .pendingExpectedStatus(null)
            .pendingExpectedEscrowStatus(null)
            .priorStatus(null)
            .priorEscrowStatus(null);
    if (pendingAction == ReservationEscrowAction.TRAINER_REJECT) {
      builder.rejectionReason(null);
    }
    return builder.build();
  }

  public boolean isLegacySchedulerEligibleAt(LocalDateTime now) {
    if (getEffectiveEscrowFlow().isUserEip7702() || status.isSchedulerInvisibleUserState()) {
      return false;
    }
    if (contractDeadlineAt != null && !now.isBefore(contractDeadlineAt)) {
      return false;
    }
    return true;
  }

  public ReservationEscrowStatus getEffectiveEscrowStatus() {
    return escrowStatus == null ? ReservationEscrowStatus.NONE : escrowStatus;
  }

  public ReservationEscrowFlow getEffectiveEscrowFlow() {
    return escrowFlow == null ? ReservationEscrowFlow.LEGACY_DISPATCH : escrowFlow;
  }

  // ============================================================
  // Private helpers
  // ============================================================

  private void guardTransition(ReservationStatus next) {
    if (!status.canTransitionTo(next)) {
      throw new MarketplaceReservationStateException(
          ErrorCode.MARKETPLACE_RESERVATION_INVALID_STATUS,
          "Cannot transition from " + status + " to " + next);
    }
  }

  private ReservationBuilder clearPendingExecutionState(ReservationBuilder builder) {
    return builder
        .currentExecutionIntentPublicId(null)
        .pendingAction(null)
        .pendingAttemptToken(null)
        .pendingActionExpiresAt(null)
        .pendingExpectedVersion(null)
        .pendingExpectedStatus(null)
        .pendingExpectedEscrowStatus(null)
        .priorStatus(null)
        .priorEscrowStatus(null);
  }
}
