package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import java.math.BigInteger;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;
import momzzangseven.mztkbe.global.error.marketplace.ClassNotFoundException;
import momzzangseven.mztkbe.global.error.marketplace.ReservationInvalidSlotDateException;
import momzzangseven.mztkbe.global.error.marketplace.TrainerSuspendedException;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.CreateReservationCommand;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.CreateReservationResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.CreateReservationUseCase;
import momzzangseven.mztkbe.modules.marketplace.classes.application.port.in.GetClassInfoUseCase;
import momzzangseven.mztkbe.modules.marketplace.classes.application.port.in.GetClassSlotInfoUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationPort;
import momzzangseven.mztkbe.modules.marketplace.sanction.application.port.out.LoadTrainerSanctionPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SaveReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SubmitEscrowTransactionPort;
import momzzangseven.mztkbe.modules.marketplace.classes.domain.model.ClassSlot;
import momzzangseven.mztkbe.modules.marketplace.classes.domain.model.MarketplaceClass;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.Reservation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service that creates a new reservation via EIP-7702 escrow.
 *
 * <p>Validation checklist:
 *
 * <ol>
 *   <li>Slot x date x time cross-validation (pessimistic lock on slot)
 *   <li>Class is active
 *   <li>Price match (signed amount == class.priceAmount)
 *   <li>Trainer not suspended
 *   <li>Slot capacity check (active count &lt; capacity)
 *   <li>Generate orderId → submit purchaseClass on-chain → persist PENDING reservation
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreateReservationService implements CreateReservationUseCase {

  private final GetClassSlotInfoUseCase getClassSlotInfoUseCase;
  private final GetClassInfoUseCase getClassInfoUseCase;
  private final LoadTrainerSanctionPort loadTrainerSanctionPort;
  private final LoadReservationPort loadReservationPort;
  private final SaveReservationPort saveReservationPort;
  private final SubmitEscrowTransactionPort submitEscrowTransactionPort;

  @Override
  @Transactional
  public CreateReservationResult execute(CreateReservationCommand command) {
    command.validate();
    log.debug(
        "CreateReservation: userId={}, classId={}, slotId={}",
        command.userId(),
        command.classId(),
        command.slotId());

    // 1. Load the target slot with pessimistic write lock — prevents over-commit under concurrency
    ClassSlot slot =
        getClassSlotInfoUseCase
            .findByIdWithLock(command.slotId())
            .orElseThrow(
                () ->
                    new BusinessException(
                        ErrorCode.MARKETPLACE_RESERVATION_INVALID_SLOT_DATE,
                        "Slot not found: classId="
                            + command.classId()
                            + " slotId="
                            + command.slotId()));

    // 1-a. Verify the slot belongs to the requested class (tamper guard)
    if (!slot.getClassId().equals(command.classId())) {
      throw new BusinessException(
          ErrorCode.MARKETPLACE_RESERVATION_INVALID_SLOT_DATE,
          "Slot "
              + command.slotId()
              + " does not belong to class "
              + command.classId());
    }

    // 2. Validate date/time against slot schedule
    if (!slot.getDaysOfWeek().contains(command.reservationDate().getDayOfWeek())) {
      throw new ReservationInvalidSlotDateException(slot.getId());
    }
    if (!slot.getStartTime().equals(command.reservationTime())) {
      throw new ReservationInvalidSlotDateException(slot.getId());
    }

    // 3. Load class and validate active status
    MarketplaceClass cls =
        getClassInfoUseCase
            .findById(command.classId())
            .orElseThrow(() -> new ClassNotFoundException(command.classId()));

    if (!cls.isActive()) {
      throw new BusinessException(
          ErrorCode.MARKETPLACE_CLASS_INACTIVE, "Class is not active: " + command.classId());
    }

    // 4. Price mismatch guard
    BigInteger expectedAmount = BigInteger.valueOf(cls.getPriceAmount());
    if (!expectedAmount.equals(command.signedAmount())) {
      throw new BusinessException(
          ErrorCode.MARKETPLACE_RESERVATION_PRICE_MISMATCH,
          "Signed amount " + command.signedAmount() + " != class price " + cls.getPriceAmount());
    }

    // 5. Trainer sanction check
    if (loadTrainerSanctionPort.hasActiveSanction(cls.getTrainerId())) {
      throw new TrainerSuspendedException();
    }

    // 6. Capacity check with pessimistic write lock — prevents over-commit under concurrency
    int activeCount = loadReservationPort.countActiveReservationsBySlotIdWithLock(slot.getId());
    if (activeCount >= slot.getCapacity()) {
      throw new BusinessException(
          ErrorCode.MARKETPLACE_RESERVATION_SLOT_FULL,
          "Slot " + slot.getId() + " is at full capacity (" + slot.getCapacity() + ")");
    }

    // 7. Generate orderId and submit on-chain purchaseClass
    String orderId = UUID.randomUUID().toString();
    String txHash =
        submitEscrowTransactionPort.submitPurchase(
            orderId, command.delegationSignature(), command.executionSignature(), expectedAmount);

    // 8. Persist PENDING reservation
    Reservation reservation =
        Reservation.createPending(
            command.userId(),
            cls.getTrainerId(),
            slot.getId(),
            command.reservationDate(),
            command.reservationTime(),
            cls.getDurationMinutes(),
            command.userRequest(),
            orderId,
            txHash);

    Reservation saved = saveReservationPort.save(reservation);
    log.info(
        "Reservation created: id={}, userId={}, classId={}",
        saved.getId(),
        saved.getUserId(),
        command.classId());

    return new CreateReservationResult(saved.getId(), saved.getStatus());
  }
}
