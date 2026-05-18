package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;
import momzzangseven.mztkbe.global.error.marketplace.ClassNotFoundException;
import momzzangseven.mztkbe.global.error.marketplace.ReservationInvalidSlotDateException;
import momzzangseven.mztkbe.global.error.marketplace.TrainerSuspendedException;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.CreateReservationCommand;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.CreateReservationResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.PrecheckReservationPurchaseCommand;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.PrepareReservationEscrowCommand;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.PrepareReservationEscrowResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationExecutionStateView;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReservationExecutionWriteView;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.CreateReservationUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.CancelReservationEscrowExecutionPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.CheckTrainerSanctionPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationClassPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationClassPort.ReservationClassSlotView;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationClassPort.ReservationClassView;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationCreateIdempotencyPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationEscrowPaymentConfigPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationExecutionStatePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationExecutionWritePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationWalletPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.PrecheckReservationPurchasePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.PrepareReservationEscrowExecutionPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.ReplayConfirmedReservationExecutionPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SaveReservationCreateIdempotencyPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SaveReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.Reservation;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.ReservationCreateIdempotency;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationCreateIdempotencyStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

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
@Service
public class CreateReservationService implements CreateReservationUseCase {

  private final LoadReservationClassPort loadReservationClassPort;
  private final CheckTrainerSanctionPort checkTrainerSanctionPort;
  private final LoadReservationPort loadReservationPort;
  private final SaveReservationPort saveReservationPort;
  private final LoadReservationCreateIdempotencyPort loadReservationCreateIdempotencyPort;
  private final SaveReservationCreateIdempotencyPort saveReservationCreateIdempotencyPort;
  private final PrecheckReservationPurchasePort precheckReservationPurchasePort;
  private final PrepareReservationEscrowExecutionPort prepareReservationEscrowExecutionPort;
  private final CancelReservationEscrowExecutionPort cancelReservationEscrowExecutionPort;
  private final LoadReservationExecutionWritePort loadReservationExecutionWritePort;
  private final LoadReservationExecutionStatePort loadReservationExecutionStatePort;
  private final ReplayConfirmedReservationExecutionPort replayConfirmedReservationExecutionPort;
  private final LoadReservationWalletPort loadReservationWalletPort;
  private final LoadReservationEscrowPaymentConfigPort loadReservationEscrowPaymentConfigPort;
  private final Clock clock;
  private TransactionOperations transactionOperations;

  public CreateReservationService(
      LoadReservationClassPort loadReservationClassPort,
      CheckTrainerSanctionPort checkTrainerSanctionPort,
      LoadReservationPort loadReservationPort,
      SaveReservationPort saveReservationPort,
      LoadReservationCreateIdempotencyPort loadReservationCreateIdempotencyPort,
      SaveReservationCreateIdempotencyPort saveReservationCreateIdempotencyPort,
      @Nullable PrecheckReservationPurchasePort precheckReservationPurchasePort,
      @Nullable PrepareReservationEscrowExecutionPort prepareReservationEscrowExecutionPort,
      @Nullable CancelReservationEscrowExecutionPort cancelReservationEscrowExecutionPort,
      @Nullable LoadReservationExecutionWritePort loadReservationExecutionWritePort,
      @Nullable LoadReservationExecutionStatePort loadReservationExecutionStatePort,
      @Nullable ReplayConfirmedReservationExecutionPort replayConfirmedReservationExecutionPort,
      @Nullable LoadReservationWalletPort loadReservationWalletPort,
      @Nullable LoadReservationEscrowPaymentConfigPort loadReservationEscrowPaymentConfigPort,
      Clock clock) {
    this.loadReservationClassPort = loadReservationClassPort;
    this.checkTrainerSanctionPort = checkTrainerSanctionPort;
    this.loadReservationPort = loadReservationPort;
    this.saveReservationPort = saveReservationPort;
    this.loadReservationCreateIdempotencyPort = loadReservationCreateIdempotencyPort;
    this.saveReservationCreateIdempotencyPort = saveReservationCreateIdempotencyPort;
    this.precheckReservationPurchasePort =
        precheckReservationPurchasePort == null
            ? DisabledReservationWeb3PortFactory.precheckPurchase()
            : precheckReservationPurchasePort;
    this.prepareReservationEscrowExecutionPort =
        prepareReservationEscrowExecutionPort == null
            ? DisabledReservationWeb3PortFactory.prepareExecution()
            : prepareReservationEscrowExecutionPort;
    this.cancelReservationEscrowExecutionPort =
        cancelReservationEscrowExecutionPort == null
            ? DisabledReservationWeb3PortFactory.cancelExecution()
            : cancelReservationEscrowExecutionPort;
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
    TransactionTemplate template = new TransactionTemplate(transactionManager);
    template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    this.transactionOperations = template;
  }

  @Override
  public CreateReservationResult execute(CreateReservationCommand command) {
    command.validate();
    log.debug(
        "CreateReservation: userId={}, classId={}, slotId={}",
        command.userId(),
        command.classId(),
        command.slotId());

    CreateReservationResult replayResult =
        runInTransaction(() -> handleExistingCreateBeforePrecheck(command));
    if (replayResult != null) {
      return replayResult;
    }

    PurchaseSnapshot purchaseSnapshot =
        runInTransaction(() -> loadPurchaseSnapshotForPrecheck(command));
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

    PhaseAResult phaseA = runInTransaction(() -> preparePurchasePhaseA(command, purchaseSnapshot));
    if (phaseA.replayResult() != null) {
      return phaseA.replayResult();
    }

    PrepareReservationEscrowResult prepared;
    try {
      prepared = prepareReservationEscrowExecutionPort.preparePurchase(phaseA.prepareCommand());
    } catch (RuntimeException e) {
      markPurchasePrepareFailed(phaseA, e);
      throw e;
    }
    try {
      markPurchaseIntentCreated(phaseA, prepared);
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
                    new BusinessException(
                        ErrorCode.MARKETPLACE_RESERVATION_INVALID_SLOT_DATE,
                        "Slot not found: classId="
                            + command.classId()
                            + " slotId="
                            + command.slotId()));

    // 1-a. Verify the slot belongs to the requested class (tamper guard)
    if (!slot.classId().equals(command.classId())) {
      throw new BusinessException(
          ErrorCode.MARKETPLACE_RESERVATION_INVALID_SLOT_DATE,
          "Slot " + command.slotId() + " does not belong to class " + command.classId());
    }

    // 1-b. Guard against booking a soft-deleted slot
    if (!slot.active()) {
      throw new BusinessException(
          ErrorCode.MARKETPLACE_RESERVATION_INVALID_SLOT_DATE,
          "Slot " + command.slotId() + " is inactive and cannot be booked");
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
      throw new BusinessException(
          ErrorCode.MARKETPLACE_RESERVATION_PAST_TIME,
          "Cannot book a session in the past: " + requestedSessionStart);
    }

    // 3. Load class and validate active status
    ReservationClassView cls =
        loadReservationClassPort
            .findClassById(command.classId())
            .orElseThrow(() -> new ClassNotFoundException(command.classId()));

    if (!cls.active()) {
      throw new BusinessException(
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
        throw new BusinessException(
            ErrorCode.MARKETPLACE_IDEMPOTENCY_CONFLICT,
            "idempotency key was reused with a different marketplace reservation payload");
      }
      if (idempotency.getStatus() == ReservationCreateIdempotencyStatus.FAILED
          || isExpired(idempotency.getExpiresAt())) {
        idempotency =
            saveReservationCreateIdempotencyPort.save(
                idempotency.restart(payloadHash, LocalDateTime.now(clock).plusMinutes(30)));
      } else if (idempotency.getReservationId() != null) {
        return PhaseAResult.replay(replayCreateResult(command.userId(), idempotency));
      } else {
        throw new BusinessException(
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
              throw new BusinessException(
                  ErrorCode.MARKETPLACE_IDEMPOTENCY_CONFLICT,
                  "active reservation already exists for the same buyer/slot/date/time");
            });
    int activeCount =
        loadReservationPort.countActiveReservationsBySlotIdAndDateWithLock(
            slot.id(), command.reservationDate());
    if (activeCount >= slot.capacity()) {
      throw new BusinessException(
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
    String orderKey = orderKeyFromUuid(orderId);
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
    PrepareReservationEscrowCommand prepareCommand =
        new PrepareReservationEscrowCommand(
            saved.getId(),
            saved.getOrderId(),
            saved.getOrderKey(),
            "BUYER",
            saved.getUserId(),
            saved.getUserId(),
            saved.getTrainerId(),
            saved.getUserId(),
            saved.getTrainerId(),
            saved.getVersion(),
            ReservationStatus.PURCHASE_PREPARING,
            ReservationEscrowStatus.PURCHASE_PREPARING,
            saved.getBuyerWalletAddress(),
            saved.getTrainerWalletAddress(),
            saved.getTokenAddress(),
            saved.getPriceBaseUnits(),
            saved.getBookedPriceAmount(),
            saved.sessionEndAt(),
            saved.getExpectedContractDeadlineEpochSeconds(),
            saved.getContractDeadlineEpochSeconds(),
            saved.getPendingAttemptToken(),
            ReservationStatus.PENDING.name());

    return PhaseAResult.pending(saved, idempotency, prepareCommand);
  }

  @Nullable
  private CreateReservationResult handleExistingCreateBeforePrecheck(
      CreateReservationCommand command) {
    String keyHash = sha256Hex(createIdempotencyKey(command));
    ReservationCreateIdempotency idempotency =
        loadReservationCreateIdempotencyPort
            .findByBuyerIdAndKeyHashWithLock(command.userId(), keyHash)
            .orElse(null);
    if (idempotency == null
        || idempotency.getStatus() == ReservationCreateIdempotencyStatus.FAILED
        || isExpired(idempotency.getExpiresAt())) {
      return null;
    }
    if (idempotency.getReservationId() == null) {
      String payloadHash = currentClassPayloadHash(command);
      if (payloadHash != null && !payloadHash.equals(idempotency.getPayloadHash())) {
        throw new BusinessException(
            ErrorCode.MARKETPLACE_IDEMPOTENCY_CONFLICT,
            "idempotency key was reused with a different marketplace reservation payload");
      }
      throw new BusinessException(
          ErrorCode.MARKETPLACE_ACTIVE_EXECUTION_CONFLICT,
          "same marketplace reservation create request is still preparing");
    }
    Reservation reservation =
        loadReservationPort
            .findById(idempotency.getReservationId())
            .orElseThrow(
                () ->
                    new BusinessException(
                        ErrorCode.MARKETPLACE_RESERVATION_NOT_FOUND,
                        "Reservation not found: " + idempotency.getReservationId()));
    String payloadHash =
        sha256Hex(
            createPayload(command, reservation.getTrainerId(), reservation.getBookedPriceAmount()));
    if (!payloadHash.equals(idempotency.getPayloadHash())) {
      throw new BusinessException(
          ErrorCode.MARKETPLACE_IDEMPOTENCY_CONFLICT,
          "idempotency key was reused with a different marketplace reservation payload");
    }
    return replayCreateResult(command.userId(), idempotency, reservation);
  }

  @Nullable
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
            .orElseThrow(
                () ->
                    new BusinessException(
                        ErrorCode.MARKETPLACE_RESERVATION_NOT_FOUND,
                        "Reservation not found: " + phaseA.reservation().getId()));
    validatePurchaseBindSnapshot(current, phaseA.reservation());
    validatePurchaseHoldStillActive(current);
    Reservation bound =
        saveReservationPort.save(
            current.bindPurchaseIntent(prepared.web3().executionIntent().id()));
    saveReservationCreateIdempotencyPort.save(
        phaseA
            .idempotency()
            .markBound(
                bound.getId(), prepared.web3().executionIntent().id(), "{\"status\":\"BOUND\"}")
            .markCompleted("{\"status\":\"COMPLETED\"}"));

    log.info(
        "Reservation purchase intent prepared: id={}, userId={}, classId={}, intentId={}",
        bound.getId(),
        bound.getUserId(),
        command.classId(),
        prepared.web3().executionIntent().id());

    return new CreateReservationResult(
        bound.getId(),
        bound.getStatus(),
        bound.getEffectiveEscrowStatus().name(),
        bound.getOrderKey(),
        prepared.web3());
  }

  private void markPurchaseIntentCreated(
      PhaseAResult phaseA, PrepareReservationEscrowResult prepared) {
    runInTransaction(
        () -> {
          saveReservationCreateIdempotencyPort.save(
              phaseA
                  .idempotency()
                  .markIntentCreated(
                      phaseA.reservation().getId(), prepared.web3().executionIntent().id()));
          return null;
        });
  }

  private BigInteger validateSignedPrice(
      LoadReservationEscrowPaymentConfigPort.ReservationEscrowPaymentConfig paymentConfig,
      CreateReservationCommand command,
      ReservationClassView cls) {
    try {
      return paymentConfig.priceBaseUnits(command.signedAmount(), cls.priceAmount());
    } catch (IllegalArgumentException e) {
      throw new BusinessException(ErrorCode.MARKETPLACE_RESERVATION_PRICE_MISMATCH, e.getMessage());
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
      throw new BusinessException(
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
      throw new BusinessException(
          ErrorCode.MARKETPLACE_ACTIVE_EXECUTION_CONFLICT,
          "marketplace class changed after purchase precheck");
    }
    String currentBuyerWallet = loadActiveWalletOrThrow(command.userId());
    String currentTrainerWallet = loadActiveWalletOrThrow(currentClass.trainerId());
    if (!purchaseSnapshot.buyerWalletAddress().equalsIgnoreCase(currentBuyerWallet)
        || !purchaseSnapshot.trainerWalletAddress().equalsIgnoreCase(currentTrainerWallet)) {
      throw new BusinessException(
          ErrorCode.MARKETPLACE_SWITCH_WALLET_REQUIRED,
          "active wallet changed after marketplace purchase precheck");
    }
  }

  private void validatePurchaseBindSnapshot(Reservation current, Reservation expected) {
    if (current.getStatus() != ReservationStatus.PURCHASE_PREPARING
        || current.getEffectiveEscrowStatus() != ReservationEscrowStatus.PURCHASE_PREPARING
        || !equalsNullable(current.getPendingAttemptToken(), expected.getPendingAttemptToken())
        || !equalsNullable(current.getVersion(), expected.getVersion())) {
      throw new BusinessException(
          ErrorCode.MARKETPLACE_ACTIVE_EXECUTION_CONFLICT,
          "marketplace purchase reservation state changed before execution intent bind");
    }
  }

  private void validatePurchaseHoldStillActive(Reservation current) {
    if (current.getHoldExpiresAt() != null
        && !LocalDateTime.now(clock).isBefore(current.getHoldExpiresAt())) {
      throw new BusinessException(
          ErrorCode.MARKETPLACE_STALE_SIGN_REQUEST,
          "marketplace purchase hold expired before execution intent bind");
    }
  }

  private void markPurchasePrepareFailed(PhaseAResult phaseA, RuntimeException cause) {
    runInTransaction(
        () -> {
          loadReservationPort
              .findByIdWithLock(phaseA.reservation().getId())
              .filter(
                  reservation ->
                      reservation.getStatus() == ReservationStatus.PURCHASE_PREPARING
                          && reservation.getCurrentExecutionIntentPublicId() == null)
              .ifPresent(
                  reservation ->
                      saveReservationPort.save(
                          reservation.markPaymentFailed(
                              cause.getClass().getSimpleName(), cause.getMessage())));
          saveReservationCreateIdempotencyPort.save(
              phaseA.idempotency().markFailed("{\"status\":\"FAILED\"}"));
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
                      reservation.getStatus() == ReservationStatus.PURCHASE_PREPARING
                          && reservation.getCurrentExecutionIntentPublicId() == null)
              .ifPresent(reservation -> saveReservationPort.save(reservation.markHoldExpired()));
          saveReservationCreateIdempotencyPort.save(
              phaseA.idempotency().markFailed("{\"status\":\"HOLD_EXPIRED\"}"));
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
      throw new BusinessException(
          ErrorCode.MARKETPLACE_IDEMPOTENCY_CONFLICT,
          "idempotency key was reused with a different marketplace reservation payload");
    }
    if (!reservation.created() && idempotency.getReservationId() == null) {
      throw new BusinessException(
          ErrorCode.MARKETPLACE_ACTIVE_EXECUTION_CONFLICT,
          "same marketplace reservation create request is still preparing");
    }
    return idempotency;
  }

  private boolean isExpired(LocalDateTime expiresAt) {
    return expiresAt != null && !LocalDateTime.now(clock).isBefore(expiresAt);
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

  private boolean canReleaseExpiredUnboundHold(Reservation reservation) {
    return reservation.getStatus() == ReservationStatus.PURCHASE_PREPARING
        && reservation.getCurrentExecutionIntentPublicId() == null
        && reservation.getHoldExpiresAt() != null
        && !LocalDateTime.now(clock).isBefore(reservation.getHoldExpiresAt());
  }

  private CreateReservationResult replayCreateResult(
      Long buyerId, ReservationCreateIdempotency idempotency) {
    Reservation reservation =
        loadReservationPort
            .findById(idempotency.getReservationId())
            .orElseThrow(
                () ->
                    new BusinessException(
                        ErrorCode.MARKETPLACE_RESERVATION_NOT_FOUND,
                        "Reservation not found: " + idempotency.getReservationId()));
    return replayCreateResult(buyerId, idempotency, reservation);
  }

  private CreateReservationResult replayCreateResult(
      Long buyerId, ReservationCreateIdempotency idempotency, Reservation reservation) {
    String intentId =
        idempotency.getCurrentExecutionIntentPublicId() != null
            ? idempotency.getCurrentExecutionIntentPublicId()
            : reservation.getCurrentExecutionIntentPublicId();
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
        resolvedReservation.getStatus(),
        resolvedReservation.getEffectiveEscrowStatus().name(),
        resolvedReservation.getOrderKey(),
        web3);
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
        .orElseThrow(
            () ->
                new BusinessException(
                    ErrorCode.WALLET_NOT_CONNECTED, "Active wallet not found: userId=" + userId));
  }

  private String createIdempotencyKey(CreateReservationCommand command) {
    if (command.idempotencyKey() != null && !command.idempotencyKey().isBlank()) {
      return command.userId() + ":" + command.idempotencyKey().trim();
    }
    return String.join(
        ":",
        "natural",
        String.valueOf(command.userId()),
        String.valueOf(command.slotId()),
        command.reservationDate().toString(),
        command.reservationTime().toString());
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

  private String orderKeyFromUuid(String orderId) {
    UUID uuid = UUID.fromString(orderId);
    return "0x"
        + "0".repeat(32)
        + String.format(
            Locale.ROOT,
            "%016x%016x",
            uuid.getMostSignificantBits(),
            uuid.getLeastSignificantBits());
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
      ReservationCreateIdempotency idempotency,
      PrepareReservationEscrowCommand prepareCommand,
      CreateReservationResult replayResult) {

    static PhaseAResult pending(
        Reservation reservation,
        ReservationCreateIdempotency idempotency,
        PrepareReservationEscrowCommand prepareCommand) {
      return new PhaseAResult(reservation, idempotency, prepareCommand, null);
    }

    static PhaseAResult replay(CreateReservationResult replayResult) {
      return new PhaseAResult(null, null, null, replayResult);
    }
  }
}
