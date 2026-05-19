package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;
import momzzangseven.mztkbe.global.error.marketplace.ClassNotFoundException;
import momzzangseven.mztkbe.global.error.marketplace.MarketplaceReservationStateException;
import momzzangseven.mztkbe.global.error.marketplace.ReservationInvalidSlotDateException;
import momzzangseven.mztkbe.global.error.marketplace.ReservationNotFoundException;
import momzzangseven.mztkbe.global.error.marketplace.TrainerSuspendedException;
import momzzangseven.mztkbe.global.error.wallet.WalletNotConnectedException;
import momzzangseven.mztkbe.global.error.web3.Web3PreparationFailureClassifier;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.CreateReservationCommand;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.CreateReservationResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.PrecheckReservationPurchaseCommand;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.PrepareReservationEscrowCommand;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.PrepareReservationEscrowResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationExecutionStateView;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationExecutionWriteView;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.CreateReservationUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.BindReservationActionStatePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.CancelReservationEscrowExecutionPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.CheckTrainerSanctionPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationActionStatePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationClassPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationClassPort.ReservationClassSlotView;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationClassPort.ReservationClassView;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationCreateIdempotencyPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationEscrowPaymentConfigPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationEscrowPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationExecutionCandidatePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationExecutionStatePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationExecutionWritePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationWalletPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.PrecheckReservationPurchasePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.PrepareReservationEscrowExecutionPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.ReplayConfirmedReservationExecutionPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.RunReservationTransactionPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SaveReservationActionStatePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SaveReservationCreateIdempotencyPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SaveReservationEscrowPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SaveReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.MarketplaceReservationActionState;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.MarketplaceReservationEscrow;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.Reservation;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.ReservationCreateIdempotency;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationActionStateStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationCreateIdempotencyStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowAction;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowActorType;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowFlow;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;

/**
 * Service that creates a new reservation via EIP-7702 escrow.
 *
 * <p>Validation checklist:
 *
 * <ol>
 *   <li>Slot x date x time cross-validation (pessimistic lock on slot)
 *   <li>Class is active
 *   <li>Price match (signed amount == class.priceAmount converted to token base units)
 *   <li>Trainer not suspended
 *   <li>Slot capacity check (active count &lt; capacity)
 *   <li>Persist a scheduler-invisible local hold, prepare a shared execution intent outside the DB
 *       lock, then relock and bind the intent.
 * </ol>
 *
 * <p><b>Two-phase ordering:</b><br>
 * The local hold and idempotency row are committed before Web3 prepare. External KMS/RPC/shared
 * execution work happens outside the reservation row lock, and Phase B relocks the row with the
 * pending-attempt token before exposing the sign request.
 */
@Slf4j
public class CreateReservationService implements CreateReservationUseCase {

  private final LoadReservationClassPort loadReservationClassPort;
  private final CheckTrainerSanctionPort checkTrainerSanctionPort;
  private final LoadReservationPort loadReservationPort;
  private final SaveReservationPort saveReservationPort;
  private final LoadReservationCreateIdempotencyPort loadReservationCreateIdempotencyPort;
  private final SaveReservationCreateIdempotencyPort saveReservationCreateIdempotencyPort;
  private final LoadReservationEscrowPort loadReservationEscrowPort;
  private final SaveReservationEscrowPort saveReservationEscrowPort;
  private final SaveReservationActionStatePort saveReservationActionStatePort;
  private final LoadReservationActionStatePort loadReservationActionStatePort;
  private final BindReservationActionStatePort bindReservationActionStatePort;
  private final PrecheckReservationPurchasePort precheckReservationPurchasePort;
  private final PrepareReservationEscrowExecutionPort prepareReservationEscrowExecutionPort;
  private final CancelReservationEscrowExecutionPort cancelReservationEscrowExecutionPort;
  private final LoadReservationExecutionWritePort loadReservationExecutionWritePort;
  private final LoadReservationExecutionStatePort loadReservationExecutionStatePort;
  private final LoadReservationExecutionCandidatePort loadReservationExecutionCandidatePort;
  private final ReservationExecutionCandidateGuard executionCandidateGuard;
  private final ReplayConfirmedReservationExecutionPort replayConfirmedReservationExecutionPort;
  private final LoadReservationWalletPort loadReservationWalletPort;
  private final LoadReservationEscrowPaymentConfigPort loadReservationEscrowPaymentConfigPort;
  private final Clock clock;
  private RunReservationTransactionPort transactionPort;

  public CreateReservationService(
      LoadReservationClassPort loadReservationClassPort,
      CheckTrainerSanctionPort checkTrainerSanctionPort,
      LoadReservationPort loadReservationPort,
      SaveReservationPort saveReservationPort,
      LoadReservationCreateIdempotencyPort loadReservationCreateIdempotencyPort,
      SaveReservationCreateIdempotencyPort saveReservationCreateIdempotencyPort,
      LoadReservationEscrowPort loadReservationEscrowPort,
      SaveReservationEscrowPort saveReservationEscrowPort,
      SaveReservationActionStatePort saveReservationActionStatePort,
      LoadReservationActionStatePort loadReservationActionStatePort,
      BindReservationActionStatePort bindReservationActionStatePort,
      PrecheckReservationPurchasePort precheckReservationPurchasePort,
      PrepareReservationEscrowExecutionPort prepareReservationEscrowExecutionPort,
      CancelReservationEscrowExecutionPort cancelReservationEscrowExecutionPort,
      LoadReservationExecutionWritePort loadReservationExecutionWritePort,
      LoadReservationExecutionStatePort loadReservationExecutionStatePort,
      ReplayConfirmedReservationExecutionPort replayConfirmedReservationExecutionPort,
      LoadReservationWalletPort loadReservationWalletPort,
      LoadReservationEscrowPaymentConfigPort loadReservationEscrowPaymentConfigPort,
      Clock clock) {
    this(
        loadReservationClassPort,
        checkTrainerSanctionPort,
        loadReservationPort,
        saveReservationPort,
        loadReservationCreateIdempotencyPort,
        saveReservationCreateIdempotencyPort,
        loadReservationEscrowPort,
        saveReservationEscrowPort,
        saveReservationActionStatePort,
        loadReservationActionStatePort,
        bindReservationActionStatePort,
        precheckReservationPurchasePort,
        prepareReservationEscrowExecutionPort,
        cancelReservationEscrowExecutionPort,
        loadReservationExecutionWritePort,
        loadReservationExecutionStatePort,
        null,
        replayConfirmedReservationExecutionPort,
        loadReservationWalletPort,
        loadReservationEscrowPaymentConfigPort,
        clock);
  }

  public CreateReservationService(
      LoadReservationClassPort loadReservationClassPort,
      CheckTrainerSanctionPort checkTrainerSanctionPort,
      LoadReservationPort loadReservationPort,
      SaveReservationPort saveReservationPort,
      LoadReservationCreateIdempotencyPort loadReservationCreateIdempotencyPort,
      SaveReservationCreateIdempotencyPort saveReservationCreateIdempotencyPort,
      LoadReservationEscrowPort loadReservationEscrowPort,
      SaveReservationEscrowPort saveReservationEscrowPort,
      SaveReservationActionStatePort saveReservationActionStatePort,
      LoadReservationActionStatePort loadReservationActionStatePort,
      BindReservationActionStatePort bindReservationActionStatePort,
      PrecheckReservationPurchasePort precheckReservationPurchasePort,
      PrepareReservationEscrowExecutionPort prepareReservationEscrowExecutionPort,
      CancelReservationEscrowExecutionPort cancelReservationEscrowExecutionPort,
      LoadReservationExecutionWritePort loadReservationExecutionWritePort,
      LoadReservationExecutionStatePort loadReservationExecutionStatePort,
      LoadReservationExecutionCandidatePort loadReservationExecutionCandidatePort,
      ReplayConfirmedReservationExecutionPort replayConfirmedReservationExecutionPort,
      LoadReservationWalletPort loadReservationWalletPort,
      LoadReservationEscrowPaymentConfigPort loadReservationEscrowPaymentConfigPort,
      Clock clock) {
    this.loadReservationClassPort = loadReservationClassPort;
    this.checkTrainerSanctionPort = checkTrainerSanctionPort;
    this.loadReservationPort = loadReservationPort;
    this.saveReservationPort = saveReservationPort;
    this.loadReservationCreateIdempotencyPort = loadReservationCreateIdempotencyPort;
    this.saveReservationCreateIdempotencyPort = saveReservationCreateIdempotencyPort;
    this.loadReservationEscrowPort = loadReservationEscrowPort;
    this.saveReservationEscrowPort = saveReservationEscrowPort;
    this.saveReservationActionStatePort = saveReservationActionStatePort;
    this.loadReservationActionStatePort = loadReservationActionStatePort;
    this.bindReservationActionStatePort = bindReservationActionStatePort;
    this.precheckReservationPurchasePort =
        java.util.Objects.requireNonNull(precheckReservationPurchasePort);
    this.prepareReservationEscrowExecutionPort =
        java.util.Objects.requireNonNull(prepareReservationEscrowExecutionPort);
    this.cancelReservationEscrowExecutionPort =
        java.util.Objects.requireNonNull(cancelReservationEscrowExecutionPort);
    this.loadReservationExecutionWritePort =
        java.util.Objects.requireNonNull(loadReservationExecutionWritePort);
    this.loadReservationExecutionStatePort =
        java.util.Objects.requireNonNull(loadReservationExecutionStatePort);
    this.loadReservationExecutionCandidatePort =
        java.util.Objects.requireNonNull(loadReservationExecutionCandidatePort);
    this.executionCandidateGuard =
        new ReservationExecutionCandidateGuard(
            this.loadReservationExecutionStatePort, this.loadReservationExecutionCandidatePort);
    this.replayConfirmedReservationExecutionPort =
        java.util.Objects.requireNonNull(replayConfirmedReservationExecutionPort);
    this.loadReservationWalletPort = java.util.Objects.requireNonNull(loadReservationWalletPort);
    this.loadReservationEscrowPaymentConfigPort =
        java.util.Objects.requireNonNull(loadReservationEscrowPaymentConfigPort);
    this.clock = clock;
  }

  public void setTransactionPort(RunReservationTransactionPort transactionPort) {
    this.transactionPort = java.util.Objects.requireNonNull(transactionPort);
  }

  @Override
  public CreateReservationResult execute(CreateReservationCommand command) {
    command.validate();
    log.debug(
        "CreateReservation: userId={}, classId={}, slotId={}",
        command.userId(),
        command.classId(),
        command.slotId());

    ExistingCreateResolution existingCreate =
        runInTransaction(() -> resolveExistingCreateBeforePrecheck(command));
    if (existingCreate.replayResult() != null) {
      return existingCreate.replayResult();
    }
    if (existingCreate.phaseA() != null) {
      return prepareAndBindPurchase(command, existingCreate.phaseA());
    }

    PurchaseSnapshot purchaseSnapshot =
        runInTransaction(() -> loadPurchaseSnapshotForPrecheck(command));
    try {
      precheckReservationPurchasePort.precheckPurchase(
          new PrecheckReservationPurchaseCommand(
              command.userId(),
              purchaseSnapshot.cls().trainerId(),
              command.classId(),
              purchaseSnapshot.slot().id(),
              command.reservationDate(),
              command.reservationTime(),
              command.signedAmount(),
              purchaseSnapshot.cls().priceAmount(),
              purchaseSnapshot.buyerWalletAddress(),
              purchaseSnapshot.trainerWalletAddress(),
              purchaseSnapshot.tokenAddress(),
              purchaseSnapshot.priceBaseUnits()));
    } catch (RuntimeException e) {
      ExistingCreateResolution concurrentWinner =
          runInTransaction(() -> resolveExistingCreateBeforePrecheck(command));
      if (concurrentWinner.replayResult() != null) {
        return concurrentWinner.replayResult();
      }
      if (concurrentWinner.phaseA() != null) {
        return prepareAndBindPurchase(command, concurrentWinner.phaseA());
      }
      persistPrecheckOnlyFailure(command, purchaseSnapshot, e);
      throw e;
    }

    PhaseAResult phaseA = preparePurchasePhaseAWithExpiredHoldRepair(command, purchaseSnapshot);
    if (phaseA.replayResult() != null) {
      return phaseA.replayResult();
    }

    return prepareAndBindPurchase(command, phaseA);
  }

  private PhaseAResult preparePurchasePhaseAWithExpiredHoldRepair(
      CreateReservationCommand command, PurchaseSnapshot purchaseSnapshot) {
    try {
      return runInTransaction(() -> preparePurchasePhaseA(command, purchaseSnapshot));
    } catch (ExpiredHoldCancelableIntentException e) {
      cancelExpiredHoldIntent(e.executionIntentId());
      return runInTransaction(() -> preparePurchasePhaseA(command, purchaseSnapshot));
    }
  }

  private void cancelExpiredHoldIntent(String executionIntentId) {
    boolean canceled =
        cancelReservationEscrowExecutionPort.cancelSignableIntent(
            executionIntentId,
            "MARKETPLACE_EXPIRED_HOLD_RELEASE",
            "marketplace purchase hold expired before execution intent was submitted");
    if (!canceled) {
      throw new MarketplaceReservationStateException(
          ErrorCode.MARKETPLACE_ACTIVE_EXECUTION_CONFLICT,
          "expired marketplace purchase hold still has a non-cancelable execution intent");
    }
  }

  private CreateReservationResult prepareAndBindPurchase(
      CreateReservationCommand command, PhaseAResult phaseA) {
    PrepareReservationEscrowResult prepared;
    try {
      prepared = prepareReservationEscrowExecutionPort.preparePurchase(phaseA.prepareCommand());
    } catch (RuntimeException e) {
      markPurchasePrepareFailed(phaseA, e);
      throw e;
    }
    try {
      return runInTransaction(() -> bindPurchasePhaseB(command, phaseA, prepared));
    } catch (RuntimeException e) {
      compensatePurchaseBindFailure(phaseA, prepared, e);
      throw e;
    }
  }

  private CreateLocalContext loadValidatedLocalContext(CreateReservationCommand command) {
    ReservationClassSlotView slot =
        loadReservationClassPort
            .findSlotByIdWithLock(command.slotId())
            .orElseThrow(
                () ->
                    new ReservationInvalidSlotDateException(
                        command.slotId(),
                        "Slot not found: classId="
                            + command.classId()
                            + " slotId="
                            + command.slotId()));

    // 1-a. Verify the slot belongs to the requested class (tamper guard)
    if (!slot.classId().equals(command.classId())) {
      throw new ReservationInvalidSlotDateException(
          command.slotId(),
          "Slot " + command.slotId() + " does not belong to class " + command.classId());
    }

    // 1-b. Guard against booking a soft-deleted slot
    if (!slot.active()) {
      throw new ReservationInvalidSlotDateException(
          command.slotId(), "Slot " + command.slotId() + " is inactive and cannot be booked");
    }

    // 2. Validate date/time against slot schedule
    if (!slot.daysOfWeek().contains(command.reservationDate().getDayOfWeek())) {
      throw new ReservationInvalidSlotDateException(slot.id());
    }
    if (!slot.startTime().equals(command.reservationTime())) {
      throw new ReservationInvalidSlotDateException(slot.id());
    }

    LocalDateTime requestedSessionStart =
        LocalDateTime.of(command.reservationDate(), command.reservationTime());
    if (requestedSessionStart.isBefore(LocalDateTime.now(clock))) {
      throw new MarketplaceReservationStateException(
          ErrorCode.MARKETPLACE_RESERVATION_PAST_TIME,
          "Cannot book a session in the past: " + requestedSessionStart);
    }

    // 3. Load class and validate active status
    ReservationClassView cls =
        loadReservationClassPort
            .findClassById(command.classId())
            .orElseThrow(() -> new ClassNotFoundException(command.classId()));

    if (!cls.active()) {
      throw new MarketplaceReservationStateException(
          ErrorCode.MARKETPLACE_CLASS_INACTIVE, "Class is not active: " + command.classId());
    }

    return new CreateLocalContext(slot, cls);
  }

  private PurchaseSnapshot loadPurchaseSnapshotForPrecheck(CreateReservationCommand command) {
    CreateLocalContext context = loadValidatedLocalContext(command);
    var paymentConfig = loadReservationEscrowPaymentConfigPort.load();
    BigInteger priceBaseUnits = validateSignedPrice(paymentConfig, command, context.cls());
    String buyerWallet = loadActiveWalletOrThrow(command.userId());
    String trainerWallet = loadActiveWalletOrThrow(context.cls().trainerId());
    Instant expectedDeadlineInstant =
        clock.instant().plusSeconds(paymentConfig.defaultDeadlineDurationSeconds());
    LocalDateTime expectedDeadlineAt =
        LocalDateTime.ofInstant(expectedDeadlineInstant, clock.getZone());
    validateCompletionWindowFits(command, context.cls(), expectedDeadlineAt);
    return new PurchaseSnapshot(
        context.slot(),
        context.cls(),
        buyerWallet,
        trainerWallet,
        paymentConfig.tokenAddress(),
        priceBaseUnits,
        expectedDeadlineInstant,
        expectedDeadlineAt);
  }

  private PhaseAResult preparePurchasePhaseA(
      CreateReservationCommand command, PurchaseSnapshot purchaseSnapshot) {
    CreateLocalContext context = loadValidatedLocalContext(command);
    validatePurchaseSnapshotStillCurrent(command, purchaseSnapshot, context);
    ReservationClassSlotView slot = context.slot();
    ReservationClassView cls = context.cls();

    // 5. Trainer sanction check
    if (checkTrainerSanctionPort.hasActiveSanction(cls.trainerId())) {
      throw new TrainerSuspendedException();
    }

    // 6. Capacity check with slot/date lock — prevents empty-date phantom over-commit under
    // concurrency.
    loadReservationPort.lockSlotDateCapacityKey(slot.id(), command.reservationDate());
    String keyHash = sha256Hex(createIdempotencyKey(command));
    String payloadHash = sha256Hex(createPayload(command, cls.trainerId(), cls.priceAmount()));
    ReservationCreateIdempotency idempotency = null;
    var existingKey =
        loadReservationCreateIdempotencyPort.findByBuyerIdAndKeyHashWithLock(
            command.userId(), keyHash);
    if (existingKey.isPresent()) {
      idempotency = existingKey.get();
      if (!payloadHash.equals(idempotency.getPayloadHash())) {
        throw new MarketplaceReservationStateException(
            ErrorCode.MARKETPLACE_IDEMPOTENCY_CONFLICT,
            "idempotency key was reused with a different marketplace reservation payload");
      }
      if ((idempotency.getStatus() == ReservationCreateIdempotencyStatus.FAILED
              || isExpired(idempotency.getExpiresAt()))
          && !hasReservationGraph(idempotency)) {
        idempotency =
            saveReservationCreateIdempotencyPort.save(
                idempotency.restart(payloadHash, LocalDateTime.now(clock).plusMinutes(30)));
      } else if (idempotency.getReservationId() != null) {
        return PhaseAResult.replay(replayCreateResult(command.userId(), idempotency));
      } else {
        throw new MarketplaceReservationStateException(
            ErrorCode.MARKETPLACE_ACTIVE_EXECUTION_CONFLICT,
            "same marketplace reservation create request is still preparing");
      }
    }
    loadReservationPort
        .findActiveByBuyerAndSlotDateTimeWithLock(
            command.userId(), slot.id(), command.reservationDate(), command.reservationTime())
        .ifPresent(
            existing -> {
              if (canReleaseExpiredUnboundHold(existing)) {
                saveReservationPort.save(existing.markHoldExpired());
                return;
              }
              findCancelableExpiredHoldIntent(existing)
                  .ifPresent(
                      intentId -> {
                        throw new ExpiredHoldCancelableIntentException(intentId);
                      });
              throw new MarketplaceReservationStateException(
                  ErrorCode.MARKETPLACE_IDEMPOTENCY_CONFLICT,
                  "active reservation already exists for the same buyer/slot/date/time");
            });
    int activeCount =
        loadReservationPort.countActiveReservationsBySlotIdAndDateWithLock(
            slot.id(), command.reservationDate());
    if (activeCount >= slot.capacity()) {
      throw new MarketplaceReservationStateException(
          ErrorCode.MARKETPLACE_RESERVATION_SLOT_FULL,
          "Slot " + slot.id() + " is at full capacity (" + slot.capacity() + ")");
    }

    if (idempotency == null) {
      idempotency = saveCreateIdempotency(command.userId(), keyHash, payloadHash);
    }
    if (idempotency.getReservationId() != null) {
      return PhaseAResult.replay(replayCreateResult(command.userId(), idempotency));
    }

    // 7. Generate orderId and persist a local hold before creating the shared execution intent.
    String orderId = UUID.randomUUID().toString();
    String orderKey = ReservationOrderKeySupport.fromOrderId(orderId);
    Reservation reservation =
        Reservation.createPending(
            command.userId(),
            cls.trainerId(),
            slot.id(),
            command.reservationDate(),
            command.reservationTime(),
            cls.durationMinutes(),
            command.userRequest(),
            orderId,
            null,
            cls.priceAmount(), // snapshot: price at booking time
            cls.title()); // snapshot: title at booking time

    String purchaseAttemptToken = UUID.randomUUID().toString();
    Reservation preparing =
        reservation.beginPurchasePreparing(
            keyHash,
            payloadHash,
            LocalDateTime.now(clock).plusMinutes(10),
            orderKey,
            purchaseSnapshot.buyerWalletAddress(),
            purchaseSnapshot.trainerWalletAddress(),
            purchaseSnapshot.tokenAddress(),
            purchaseSnapshot.priceBaseUnits().toString(),
            purchaseSnapshot.expectedDeadlineInstant().getEpochSecond(),
            purchaseSnapshot.expectedDeadlineAt(),
            purchaseAttemptToken);
    Reservation saved = saveReservationPort.save(preparing);
    String rootIdempotencyKey = purchaseRootIdempotencyKey(saved.getUserId(), keyHash);
    MarketplaceReservationEscrow escrow =
        saveReservationEscrowPort.save(
            MarketplaceReservationEscrow.builder()
                .reservationId(saved.getId())
                .escrowFlow(ReservationEscrowFlow.USER_EIP7702)
                .escrowStatus(ReservationEscrowStatus.NONE)
                .orderKey(saved.getOrderKey())
                .buyerWalletAddress(saved.getBuyerWalletAddress())
                .trainerWalletAddress(saved.getTrainerWalletAddress())
                .tokenAddress(saved.getTokenAddress())
                .priceBaseUnits(purchaseSnapshot.priceBaseUnits())
                .holdExpiresAt(saved.getHoldExpiresAt())
                .expectedContractDeadlineEpochSeconds(
                    saved.getExpectedContractDeadlineEpochSeconds())
                .expectedContractDeadlineAt(saved.getExpectedContractDeadlineAt())
                .build());
    MarketplaceReservationActionState actionState =
        saveReservationActionStatePort.save(
            MarketplaceReservationActionState.builder()
                .reservationId(saved.getId())
                .escrowId(escrow.getId())
                .actionType(ReservationEscrowAction.PURCHASE)
                .actorType(ReservationEscrowActorType.BUYER)
                .actorUserId(saved.getUserId())
                .attemptNo(1)
                .attemptToken(purchaseAttemptToken)
                .status(ReservationActionStateStatus.PREPARING)
                .rootIdempotencyKey(rootIdempotencyKey)
                .expectedReservationVersion(saved.getVersion())
                .expectedReservationStatus(saved.getStatus())
                .expectedEscrowStatus(escrow.getEscrowStatus())
                .priorReservationStatus(ReservationStatus.PENDING)
                .priorEscrowStatus(ReservationEscrowStatus.NONE)
                .preparationExpiresAt(saved.getHoldExpiresAt())
                .build());
    ReservationCreateIdempotency attachedIdempotency =
        saveReservationCreateIdempotencyPort.save(
            idempotency
                .attachReservationGraph(saved.getId(), escrow.getId(), actionState.getId())
                .markBound("{\"status\":\"BOUND\"}"));
    PrepareReservationEscrowCommand prepareCommand =
        preparePurchaseCommand(saved, escrow.getId(), actionState);

    return PhaseAResult.pending(saved, escrow, actionState, attachedIdempotency, prepareCommand);
  }

  private PhaseAResult beginRetryablePurchaseAttempt(
      CreateReservationCommand command,
      ReservationCreateIdempotency idempotency,
      Reservation unlockedReservation) {
    if (!hasReservationGraph(idempotency) || idempotency.getActionStateId() == null) {
      return null;
    }
    Reservation reservation =
        loadReservationPort
            .findByIdWithLock(unlockedReservation.getId())
            .orElseThrow(() -> new ReservationNotFoundException(unlockedReservation.getId()));
    MarketplaceReservationActionState latestAction =
        loadReservationActionStatePort
            .findLatestByReservationIdAndActionTypeWithLock(
                reservation.getId(), ReservationEscrowAction.PURCHASE)
            .orElse(null);
    if (!isRetryablePurchasePreparationFailure(idempotency, reservation, latestAction)) {
      return null;
    }
    validatePurchaseHoldStillActive(reservation);
    if (executionCandidateGuard.hasBlockingExecution(reservation, latestAction)) {
      return null;
    }

    String nextAttemptToken = UUID.randomUUID().toString();
    LocalDateTime nextHoldExpiresAt = LocalDateTime.now(clock).plusMinutes(10);
    Reservation retrying =
        saveReservationPort.save(
            reservation.retryPurchasePreparing(nextAttemptToken, nextHoldExpiresAt));
    MarketplaceReservationEscrow retryingEscrow =
        saveReservationEscrowPort.save(
            loadReservationEscrowPort
                .findByReservationIdWithLock(retrying.getId())
                .orElseThrow(
                    () ->
                        new MarketplaceReservationStateException(
                            ErrorCode.MARKETPLACE_ACTIVE_EXECUTION_CONFLICT,
                            "marketplace reservation escrow projection is missing before retry"))
                .toBuilder()
                .holdExpiresAt(retrying.getHoldExpiresAt())
                .build());
    MarketplaceReservationActionState staleAction =
        saveReservationActionStatePort.save(
            latestAction.toBuilder()
                .status(ReservationActionStateStatus.STALE)
                .retryable(false)
                .errorCode("RETRY_SUPERSEDED")
                .errorReason("marketplace purchase retry created a newer action-state")
                .build());
    MarketplaceReservationActionState nextAction =
        saveReservationActionStatePort.save(
            MarketplaceReservationActionState.builder()
                .reservationId(retrying.getId())
                .escrowId(retryingEscrow.getId())
                .actionType(ReservationEscrowAction.PURCHASE)
                .actorType(ReservationEscrowActorType.BUYER)
                .actorUserId(retrying.getUserId())
                .attemptNo(staleAction.getAttemptNo() + 1)
                .attemptToken(nextAttemptToken)
                .status(ReservationActionStateStatus.PREPARING)
                .rootIdempotencyKey(staleAction.getRootIdempotencyKey())
                .payloadHash(staleAction.getPayloadHash())
                .expectedReservationVersion(retrying.getVersion())
                .expectedReservationStatus(ReservationStatus.HOLDING)
                .expectedEscrowStatus(staleAction.getExpectedEscrowStatus())
                .priorReservationStatus(ReservationStatus.PENDING)
                .priorEscrowStatus(staleAction.getPriorEscrowStatus())
                .preparationExpiresAt(retrying.getHoldExpiresAt())
                .build());
    ReservationCreateIdempotency updatedIdempotency =
        replaceCreateIdempotencyActionState(idempotency, staleAction.getId(), nextAction.getId());
    PrepareReservationEscrowCommand prepareCommand =
        preparePurchaseCommand(retrying, staleAction.getEscrowId(), nextAction);
    log.info(
        "Reservation purchase retry prepared: id={}, userId={}, classId={}, previousActionStateId={},"
            + " nextActionStateId={}",
        retrying.getId(),
        retrying.getUserId(),
        command.classId(),
        staleAction.getId(),
        nextAction.getId());
    return PhaseAResult.pending(
        retrying, retryingEscrow, nextAction, updatedIdempotency, prepareCommand);
  }

  private ReservationCreateIdempotency replaceCreateIdempotencyActionState(
      ReservationCreateIdempotency idempotency, Long expectedActionStateId, Long newActionStateId) {
    if (idempotency.getId() == null) {
      return saveReservationCreateIdempotencyPort.save(
          idempotency.replaceActionState(newActionStateId).markBound("{\"status\":\"BOUND\"}"));
    }
    return saveReservationCreateIdempotencyPort
        .replaceActionStateIfCurrent(idempotency.getId(), expectedActionStateId, newActionStateId)
        .orElseThrow(
            () ->
                new MarketplaceReservationStateException(
                    ErrorCode.MARKETPLACE_ACTIVE_EXECUTION_CONFLICT,
                    "marketplace purchase idempotency pointer changed before retry"));
  }

  private boolean isRetryablePurchasePreparationFailure(
      ReservationCreateIdempotency idempotency,
      Reservation reservation,
      MarketplaceReservationActionState actionState) {
    return actionState != null
        && equalsNullable(idempotency.getActionStateId(), actionState.getId())
        && equalsNullable(idempotency.getReservationId(), actionState.getReservationId())
        && equalsNullable(idempotency.getEscrowId(), actionState.getEscrowId())
        && actionState.getActionType() == ReservationEscrowAction.PURCHASE
        && actionState.getStatus() == ReservationActionStateStatus.PREPARATION_FAILED
        && Boolean.TRUE.equals(actionState.getRetryable())
        && isPurchaseHolding(reservation)
        && reservation.getCurrentExecutionIntentPublicId() == null;
  }

  private void rejectActiveUnboundPurchaseAttempt(
      ReservationCreateIdempotency idempotency, Reservation reservation) {
    if (idempotency.getActionStateId() == null) {
      return;
    }
    loadReservationActionStatePort
        .findById(idempotency.getActionStateId())
        .filter(actionState -> reservation.getId().equals(actionState.getReservationId()))
        .filter(actionState -> actionState.getActionType() == ReservationEscrowAction.PURCHASE)
        .filter(actionState -> actionState.getStatus() == ReservationActionStateStatus.PREPARING)
        .filter(
            actionState ->
                actionState.getExecutionIntentPublicId() == null
                    || actionState.getExecutionIntentPublicId().isBlank())
        .ifPresent(
            actionState -> {
              throw new MarketplaceReservationStateException(
                  ErrorCode.MARKETPLACE_ACTIVE_EXECUTION_CONFLICT,
                  "same marketplace reservation create request is still preparing");
            });
  }

  private PrepareReservationEscrowCommand preparePurchaseCommand(
      Reservation reservation, Long escrowId, MarketplaceReservationActionState actionState) {
    return new PrepareReservationEscrowCommand(
        reservation.getId(),
        reservation.getOrderId(),
        reservation.getOrderKey(),
        "BUYER",
        reservation.getUserId(),
        reservation.getUserId(),
        reservation.getTrainerId(),
        reservation.getUserId(),
        reservation.getTrainerId(),
        reservation.getVersion(),
        ReservationStatus.HOLDING,
        ReservationEscrowStatus.PURCHASE_PREPARING,
        reservation.getBuyerWalletAddress(),
        reservation.getTrainerWalletAddress(),
        reservation.getTokenAddress(),
        reservation.getPriceBaseUnits(),
        reservation.getBookedPriceAmount(),
        reservation.sessionEndAt(),
        reservation.getExpectedContractDeadlineEpochSeconds(),
        reservation.getContractDeadlineEpochSeconds(),
        reservation.getPendingAttemptToken(),
        ReservationStatus.PENDING.name(),
        escrowId,
        actionState.getId(),
        actionState.getRootIdempotencyKey());
  }

  private ExistingCreateResolution resolveExistingCreateBeforePrecheck(
      CreateReservationCommand command) {
    String keyHash = sha256Hex(createIdempotencyKey(command));
    ReservationCreateIdempotency idempotency =
        loadReservationCreateIdempotencyPort
            .findByBuyerIdAndKeyHashWithLock(command.userId(), keyHash)
            .orElse(null);
    if (idempotency == null) {
      return ExistingCreateResolution.none();
    }
    if ((idempotency.getStatus() == ReservationCreateIdempotencyStatus.FAILED
            || isExpired(idempotency.getExpiresAt()))
        && !hasReservationGraph(idempotency)) {
      return ExistingCreateResolution.none();
    }
    if (idempotency.getReservationId() == null) {
      String payloadHash = currentClassPayloadHash(command);
      if (payloadHash != null && !payloadHash.equals(idempotency.getPayloadHash())) {
        throw new MarketplaceReservationStateException(
            ErrorCode.MARKETPLACE_IDEMPOTENCY_CONFLICT,
            "idempotency key was reused with a different marketplace reservation payload");
      }
      throw new MarketplaceReservationStateException(
          ErrorCode.MARKETPLACE_ACTIVE_EXECUTION_CONFLICT,
          "same marketplace reservation create request is still preparing");
    }
    Reservation reservation =
        loadReservationPort
            .findById(idempotency.getReservationId())
            .orElseThrow(() -> new ReservationNotFoundException(idempotency.getReservationId()));
    String payloadHash =
        sha256Hex(
            createPayload(command, reservation.getTrainerId(), reservation.getBookedPriceAmount()));
    if (!payloadHash.equals(idempotency.getPayloadHash())) {
      throw new MarketplaceReservationStateException(
          ErrorCode.MARKETPLACE_IDEMPOTENCY_CONFLICT,
          "idempotency key was reused with a different marketplace reservation payload");
    }
    PhaseAResult retry = beginRetryablePurchaseAttempt(command, idempotency, reservation);
    if (retry != null) {
      return ExistingCreateResolution.phaseA(retry);
    }
    rejectActiveUnboundPurchaseAttempt(idempotency, reservation);
    return ExistingCreateResolution.replay(
        replayCreateResult(command.userId(), idempotency, reservation));
  }

  private String currentClassPayloadHash(CreateReservationCommand command) {
    return loadReservationClassPort
        .findClassById(command.classId())
        .map(cls -> sha256Hex(createPayload(command, cls.trainerId(), cls.priceAmount())))
        .orElse(null);
  }

  private CreateReservationResult bindPurchasePhaseB(
      CreateReservationCommand command,
      PhaseAResult phaseA,
      PrepareReservationEscrowResult prepared) {
    Reservation current =
        loadReservationPort
            .findByIdWithLock(phaseA.reservation().getId())
            .orElseThrow(() -> new ReservationNotFoundException(phaseA.reservation().getId()));
    validatePurchaseBindSnapshot(current, phaseA.reservation());
    validatePurchaseHoldStillActive(current);
    MarketplaceReservationActionState boundActionState =
        bindReservationActionStatePort
            .bindExecutionIntent(
                phaseA.actionState().getId(),
                phaseA.actionState().getAttemptToken(),
                prepared.web3().executionIntent().id())
            .orElseThrow(
                () ->
                    new MarketplaceReservationStateException(
                        ErrorCode.MARKETPLACE_ACTIVE_EXECUTION_CONFLICT,
                        "marketplace purchase action state changed before execution intent bind"));
    Reservation bound =
        saveReservationPort.save(
            current.bindPurchaseIntent(prepared.web3().executionIntent().id()));
    saveReservationCreateIdempotencyPort.save(
        phaseA
            .idempotency()
            .markBound("{\"status\":\"BOUND\"}")
            .markCompleted("{\"status\":\"COMPLETED\"}"));

    log.info(
        "Reservation purchase intent prepared: id={}, userId={}, classId={}, intentId={}",
        bound.getId(),
        bound.getUserId(),
        command.classId(),
        prepared.web3().executionIntent().id());

    return new CreateReservationResult(
        bound.getId(),
        ReservationDisplayStatusMapper.displayStatus(bound),
        ReservationDisplayStatusMapper.businessStatus(bound),
        bound.getEffectiveEscrowStatus().name(),
        bound.getOrderKey(),
        prepared.web3());
  }

  private BigInteger validateSignedPrice(
      LoadReservationEscrowPaymentConfigPort.ReservationEscrowPaymentConfig paymentConfig,
      CreateReservationCommand command,
      ReservationClassView cls) {
    try {
      return paymentConfig.priceBaseUnits(command.signedAmount(), cls.priceAmount());
    } catch (IllegalArgumentException e) {
      throw new MarketplaceReservationStateException(
          ErrorCode.MARKETPLACE_RESERVATION_PRICE_MISMATCH, e.getMessage());
    }
  }

  private void validateCompletionWindowFits(
      CreateReservationCommand command,
      ReservationClassView cls,
      LocalDateTime expectedDeadlineAt) {
    if (LocalDateTime.of(command.reservationDate(), command.reservationTime())
        .plusMinutes(cls.durationMinutes())
        .plusHours(24)
        .isAfter(expectedDeadlineAt)) {
      throw new MarketplaceReservationStateException(
          ErrorCode.MARKETPLACE_DEADLINE_EXECUTION_WINDOW_EXPIRED,
          "Reservation completion window does not fit before the marketplace escrow deadline");
    }
  }

  private void validatePurchaseSnapshotStillCurrent(
      CreateReservationCommand command,
      PurchaseSnapshot purchaseSnapshot,
      CreateLocalContext currentContext) {
    ReservationClassView currentClass = currentContext.cls();
    if (!purchaseSnapshot.cls().trainerId().equals(currentClass.trainerId())
        || purchaseSnapshot.cls().priceAmount() != currentClass.priceAmount()
        || purchaseSnapshot.cls().durationMinutes() != currentClass.durationMinutes()) {
      throw new MarketplaceReservationStateException(
          ErrorCode.MARKETPLACE_ACTIVE_EXECUTION_CONFLICT,
          "marketplace class changed after purchase precheck");
    }
    String currentBuyerWallet = loadActiveWalletOrThrow(command.userId());
    String currentTrainerWallet = loadActiveWalletOrThrow(currentClass.trainerId());
    if (!purchaseSnapshot.buyerWalletAddress().equalsIgnoreCase(currentBuyerWallet)
        || !purchaseSnapshot.trainerWalletAddress().equalsIgnoreCase(currentTrainerWallet)) {
      throw new MarketplaceReservationStateException(
          ErrorCode.MARKETPLACE_SWITCH_WALLET_REQUIRED,
          "active wallet changed after marketplace purchase precheck");
    }
  }

  private void validatePurchaseBindSnapshot(Reservation current, Reservation expected) {
    if ((current.getStatus() != ReservationStatus.PURCHASE_PREPARING
            && current.getStatus() != ReservationStatus.HOLDING)
        || current.getEffectiveEscrowStatus() != ReservationEscrowStatus.PURCHASE_PREPARING
        || !equalsNullable(current.getPendingAttemptToken(), expected.getPendingAttemptToken())
        || !equalsNullable(current.getVersion(), expected.getVersion())) {
      throw new MarketplaceReservationStateException(
          ErrorCode.MARKETPLACE_ACTIVE_EXECUTION_CONFLICT,
          "marketplace purchase reservation state changed before execution intent bind");
    }
  }

  private void validatePurchaseHoldStillActive(Reservation current) {
    if (current.getHoldExpiresAt() != null
        && !LocalDateTime.now(clock).isBefore(current.getHoldExpiresAt())) {
      throw new MarketplaceReservationStateException(
          ErrorCode.MARKETPLACE_STALE_SIGN_REQUEST,
          "marketplace purchase hold expired before execution intent bind");
    }
  }

  private void markPurchasePrepareFailed(PhaseAResult phaseA, RuntimeException cause) {
    boolean retryable = Web3PreparationFailureClassifier.isRetryable(cause);
    runInTransaction(
        () -> {
          if (!retryable) {
            loadReservationPort
                .findByIdWithLock(phaseA.reservation().getId())
                .filter(
                    reservation ->
                        isPurchaseHolding(reservation)
                            && reservation.getCurrentExecutionIntentPublicId() == null)
                .ifPresent(
                    reservation ->
                        saveReservationPort.save(
                            reservation.markPaymentFailed(
                                preparationFailureErrorCode(cause), cause.getMessage())));
          }
          saveReservationActionStatePort.save(
              phaseA.actionState().toBuilder()
                  .status(ReservationActionStateStatus.PREPARATION_FAILED)
                  .retryable(retryable)
                  .errorCode(preparationFailureErrorCode(cause))
                  .errorReason(cause.getMessage())
                  .build());
          saveReservationCreateIdempotencyPort.save(
              retryable
                  ? phaseA.idempotency().markBound("{\"status\":\"PREPARATION_FAILED\"}")
                  : phaseA.idempotency().markCompleted("{\"status\":\"PAYMENT_FAILED\"}"));
          return null;
        });
  }

  private void compensatePurchaseBindFailure(
      PhaseAResult phaseA, PrepareReservationEscrowResult prepared, RuntimeException cause) {
    if (!cancelSignablePreparedIntent(prepared, cause)) {
      return;
    }
    if (isExpiredPurchaseHold(cause)) {
      markPurchaseHoldExpired(phaseA);
      return;
    }
    markPurchasePrepareFailed(phaseA, cause);
  }

  private boolean isExpiredPurchaseHold(RuntimeException cause) {
    return cause instanceof BusinessException businessException
        && ErrorCode.MARKETPLACE_STALE_SIGN_REQUEST.getCode().equals(businessException.getCode());
  }

  private void markPurchaseHoldExpired(PhaseAResult phaseA) {
    runInTransaction(
        () -> {
          loadReservationPort
              .findByIdWithLock(phaseA.reservation().getId())
              .filter(
                  reservation ->
                      isPurchaseHolding(reservation)
                          && reservation.getCurrentExecutionIntentPublicId() == null)
              .ifPresent(reservation -> saveReservationPort.save(reservation.markHoldExpired()));
          saveReservationActionStatePort.save(
              phaseA.actionState().toBuilder()
                  .status(ReservationActionStateStatus.PREPARATION_FAILED)
                  .retryable(false)
                  .errorCode("HOLD_EXPIRED")
                  .errorReason("marketplace purchase hold expired before execution intent bind")
                  .build());
          saveReservationCreateIdempotencyPort.save(
              phaseA.idempotency().markCompleted("{\"status\":\"HOLD_EXPIRED\"}"));
          return null;
        });
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
          "Failed to cancel marketplace purchase intent after Phase B bind failure: intentId={}",
          executionIntentId,
          cancelFailure);
      return false;
    }
  }

  private ReservationCreateIdempotency saveCreateIdempotency(
      Long buyerId, String keyHash, String payloadHash) {
    SaveReservationCreateIdempotencyPort.ReserveCreateIdempotencyResult reservation =
        saveReservationCreateIdempotencyPort.reservePreparing(
            buyerId, keyHash, payloadHash, LocalDateTime.now(clock).plusMinutes(30));
    ReservationCreateIdempotency idempotency = reservation.idempotency();
    if (!payloadHash.equals(idempotency.getPayloadHash())) {
      throw new MarketplaceReservationStateException(
          ErrorCode.MARKETPLACE_IDEMPOTENCY_CONFLICT,
          "idempotency key was reused with a different marketplace reservation payload");
    }
    if (!reservation.created() && idempotency.getReservationId() == null) {
      throw new MarketplaceReservationStateException(
          ErrorCode.MARKETPLACE_ACTIVE_EXECUTION_CONFLICT,
          "same marketplace reservation create request is still preparing");
    }
    return idempotency;
  }

  private void persistPrecheckOnlyFailure(
      CreateReservationCommand command, PurchaseSnapshot purchaseSnapshot, RuntimeException cause) {
    runInTransaction(
        () -> {
          String keyHash = sha256Hex(createIdempotencyKey(command));
          String payloadHash =
              sha256Hex(
                  createPayload(
                      command,
                      purchaseSnapshot.cls().trainerId(),
                      purchaseSnapshot.cls().priceAmount()));
          SaveReservationCreateIdempotencyPort.ReserveCreateIdempotencyResult reserved =
              saveReservationCreateIdempotencyPort.reservePreparing(
                  command.userId(), keyHash, payloadHash, LocalDateTime.now(clock).plusMinutes(30));
          ReservationCreateIdempotency idempotency = reserved.idempotency();
          if (payloadHash.equals(idempotency.getPayloadHash())
              && !hasReservationGraph(idempotency)) {
            saveReservationCreateIdempotencyPort.save(
                idempotency.markFailed(precheckFailureSnapshot(cause)));
          }
          return null;
        });
  }

  private String precheckFailureSnapshot(RuntimeException cause) {
    return "{\"status\":\"PRECHECK_FAILED\",\"errorCode\":\""
        + preparationFailureErrorCode(cause)
        + "\"}";
  }

  private String purchaseRootIdempotencyKey(Long buyerId, String keyHash) {
    return "buyer:" + buyerId + ":create:" + keyHash;
  }

  private boolean isExpired(LocalDateTime expiresAt) {
    return expiresAt != null && !LocalDateTime.now(clock).isBefore(expiresAt);
  }

  private boolean equalsNullable(Object left, Object right) {
    return left == null ? right == null : left.equals(right);
  }

  private boolean hasReservationGraph(ReservationCreateIdempotency idempotency) {
    return idempotency.getReservationId() != null
        && idempotency.getEscrowId() != null
        && idempotency.getActionStateId() != null;
  }

  private String preparationFailureErrorCode(RuntimeException failure) {
    if (failure instanceof BusinessException businessFailure) {
      return businessFailure.getCode();
    }
    return failure.getClass().getSimpleName();
  }

  private <T> T runInTransaction(java.util.function.Supplier<T> supplier) {
    return transactionPort.requiresNew(supplier);
  }

  private boolean canReleaseExpiredUnboundHold(Reservation reservation) {
    if (!isExpiredUnboundPurchaseHold(reservation)) {
      return false;
    }
    MarketplaceReservationActionState actionState =
        loadReservationActionStatePort
            .findLatestByReservationIdAndActionType(
                reservation.getId(), ReservationEscrowAction.PURCHASE)
            .orElse(null);
    if (actionState == null) {
      return true;
    }
    if (!actionState.getStatus().isActive()) {
      return true;
    }
    return !executionCandidateGuard.hasBlockingExecution(reservation, actionState);
  }

  private boolean isExpiredUnboundPurchaseHold(Reservation reservation) {
    return isPurchaseHolding(reservation)
        && reservation.getCurrentExecutionIntentPublicId() == null
        && reservation.getHoldExpiresAt() != null
        && !LocalDateTime.now(clock).isBefore(reservation.getHoldExpiresAt());
  }

  private java.util.Optional<String> findCancelableExpiredHoldIntent(Reservation reservation) {
    if (!isExpiredUnboundPurchaseHold(reservation)) {
      return java.util.Optional.empty();
    }
    MarketplaceReservationActionState actionState =
        loadReservationActionStatePort
            .findLatestByReservationIdAndActionType(
                reservation.getId(), ReservationEscrowAction.PURCHASE)
            .orElse(null);
    if (actionState == null || !actionState.getStatus().isActive()) {
      return java.util.Optional.empty();
    }
    return executionCandidateGuard.findAwaitingSignatureIntent(reservation, actionState);
  }

  private boolean isPurchaseHolding(Reservation reservation) {
    return reservation.getStatus() == ReservationStatus.PURCHASE_PREPARING
        || reservation.getStatus() == ReservationStatus.HOLDING;
  }

  private CreateReservationResult replayCreateResult(
      Long buyerId, ReservationCreateIdempotency idempotency) {
    Reservation reservation =
        loadReservationPort
            .findById(idempotency.getReservationId())
            .orElseThrow(() -> new ReservationNotFoundException(idempotency.getReservationId()));
    return replayCreateResult(buyerId, idempotency, reservation);
  }

  private CreateReservationResult replayCreateResult(
      Long buyerId, ReservationCreateIdempotency idempotency, Reservation reservation) {
    String intentId = resolveCreateExecutionIntentId(idempotency, reservation);
    Reservation resolvedReservation = replayConfirmedPurchaseIfNeeded(reservation, intentId);
    boolean replayChangedReservation =
        resolvedReservation.getStatus() != reservation.getStatus()
            || !equalsNullable(
                resolvedReservation.getCurrentExecutionIntentPublicId(),
                reservation.getCurrentExecutionIntentPublicId());
    if (replayChangedReservation) {
      intentId = resolvedReservation.getCurrentExecutionIntentPublicId();
    }
    ReservationExecutionWriteView web3 =
        intentId == null || intentId.isBlank()
            ? null
            : loadReservationExecutionWritePort
                .load(buyerId, intentId)
                .asExistingForOrder(resolvedReservation.getOrderKey());
    return new CreateReservationResult(
        resolvedReservation.getId(),
        ReservationDisplayStatusMapper.displayStatus(resolvedReservation),
        ReservationDisplayStatusMapper.businessStatus(resolvedReservation),
        resolvedReservation.getEffectiveEscrowStatus().name(),
        resolvedReservation.getOrderKey(),
        web3);
  }

  private String resolveCreateExecutionIntentId(
      ReservationCreateIdempotency idempotency, Reservation reservation) {
    if (idempotency.getActionStateId() != null) {
      return loadReservationActionStatePort
          .findById(idempotency.getActionStateId())
          .filter(actionState -> reservation.getId().equals(actionState.getReservationId()))
          .filter(actionState -> actionState.getActionType() == ReservationEscrowAction.PURCHASE)
          .map(MarketplaceReservationActionState::getExecutionIntentPublicId)
          .orElse(null);
    }
    return reservation.getCurrentExecutionIntentPublicId();
  }

  private Reservation replayConfirmedPurchaseIfNeeded(Reservation reservation, String intentId) {
    if (intentId == null || intentId.isBlank()) {
      return reservation;
    }
    ReservationExecutionStateView state = loadReservationExecutionStatePort.loadState(intentId);
    if (!isConfirmedOutcome(state)) {
      return reservation;
    }
    if (!replayConfirmedReservationExecutionPort.replayConfirmed(
        state.executionIntentId(), state.actionType())) {
      return reservation;
    }
    return loadReservationPort.findById(reservation.getId()).orElse(reservation);
  }

  private boolean isConfirmedOutcome(ReservationExecutionStateView state) {
    return state != null
        && ("CONFIRMED".equals(state.status()) || "SUCCEEDED".equals(state.transactionStatus()));
  }

  private String loadActiveWalletOrThrow(Long userId) {
    return loadReservationWalletPort
        .loadActiveWalletAddress(userId)
        .orElseThrow(() -> new WalletNotConnectedException(userId));
  }

  private String createIdempotencyKey(CreateReservationCommand command) {
    return command.userId() + ":" + command.idempotencyKey().trim();
  }

  private String createPayload(
      CreateReservationCommand command, Long trainerId, Integer bookedPriceAmountKrw) {
    return String.join(
        ":",
        String.valueOf(command.userId()),
        String.valueOf(command.classId()),
        String.valueOf(command.slotId()),
        command.reservationDate().toString(),
        command.reservationTime().toString(),
        command.signedAmount().toString(),
        String.valueOf(trainerId),
        String.valueOf(bookedPriceAmountKrw));
  }

  private String sha256Hex(String value) {
    try {
      return "0x"
          + toHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException("failed to hash marketplace reservation create input", e);
    }
  }

  private String toHex(byte[] bytes) {
    char[] chars = new char[bytes.length * 2];
    for (int i = 0; i < bytes.length; i++) {
      int value = bytes[i] & 0xff;
      chars[i * 2] = Character.forDigit(value >>> 4, 16);
      chars[i * 2 + 1] = Character.forDigit(value & 0x0f, 16);
    }
    return new String(chars);
  }

  private record CreateLocalContext(ReservationClassSlotView slot, ReservationClassView cls) {}

  private record PurchaseSnapshot(
      ReservationClassSlotView slot,
      ReservationClassView cls,
      String buyerWalletAddress,
      String trainerWalletAddress,
      String tokenAddress,
      BigInteger priceBaseUnits,
      Instant expectedDeadlineInstant,
      LocalDateTime expectedDeadlineAt) {}

  private record PhaseAResult(
      Reservation reservation,
      MarketplaceReservationEscrow escrow,
      MarketplaceReservationActionState actionState,
      ReservationCreateIdempotency idempotency,
      PrepareReservationEscrowCommand prepareCommand,
      CreateReservationResult replayResult) {

    static PhaseAResult pending(
        Reservation reservation,
        MarketplaceReservationEscrow escrow,
        MarketplaceReservationActionState actionState,
        ReservationCreateIdempotency idempotency,
        PrepareReservationEscrowCommand prepareCommand) {
      return new PhaseAResult(reservation, escrow, actionState, idempotency, prepareCommand, null);
    }

    static PhaseAResult replay(CreateReservationResult replayResult) {
      return new PhaseAResult(null, null, null, null, null, replayResult);
    }
  }

  private record ExistingCreateResolution(
      CreateReservationResult replayResult, PhaseAResult phaseA) {

    static ExistingCreateResolution none() {
      return new ExistingCreateResolution(null, null);
    }

    static ExistingCreateResolution replay(CreateReservationResult replayResult) {
      return new ExistingCreateResolution(replayResult, null);
    }

    static ExistingCreateResolution phaseA(PhaseAResult phaseA) {
      return new ExistingCreateResolution(null, phaseA);
    }
  }

  private static final class ExpiredHoldCancelableIntentException extends RuntimeException {

    private final String executionIntentId;

    private ExpiredHoldCancelableIntentException(String executionIntentId) {
      super("expired marketplace purchase hold has a cancelable execution intent");
      this.executionIntentId = executionIntentId;
    }

    private String executionIntentId() {
      return executionIntentId;
    }
  }
}
