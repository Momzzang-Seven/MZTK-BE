package momzzangseven.mztkbe.modules.marketplace.reservation.application.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.ErrorCode;
import momzzangseven.mztkbe.global.error.marketplace.MarketplaceReservationStateException;
import momzzangseven.mztkbe.global.error.marketplace.MarketplaceUnauthorizedAccessException;
import momzzangseven.mztkbe.global.error.marketplace.ReservationNotFoundException;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ApproveReservationCommand;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ApproveReservationResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.ApproveReservationUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationActionStatePort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationEscrowPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationWalletPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.RunReservationTransactionPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SaveReservationPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.MarketplaceReservationEscrow;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.Reservation;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationActionStateStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;

/**
 * Service for a trainer to approve a PENDING reservation.
 *
 * <p>No on-chain transaction is emitted; funds remain locked in escrow.
 */
@Slf4j
public class ApproveReservationService implements ApproveReservationUseCase {

  private static final long APPROVE_TIMEOUT_HOURS = 72L;
  private static final long SESSION_START_GUARD_HOURS = 1L;

  private final LoadReservationPort loadReservationPort;
  private final SaveReservationPort saveReservationPort;
  private final LoadReservationEscrowPort loadReservationEscrowPort;
  private final LoadReservationActionStatePort loadReservationActionStatePort;
  private final LoadReservationWalletPort loadReservationWalletPort;
  private final Clock clock;
  private RunReservationTransactionPort transactionPort;

  public ApproveReservationService(
      LoadReservationPort loadReservationPort,
      SaveReservationPort saveReservationPort,
      Clock clock) {
    this(loadReservationPort, saveReservationPort, null, null, null, clock);
  }

  public ApproveReservationService(
      LoadReservationPort loadReservationPort,
      SaveReservationPort saveReservationPort,
      LoadReservationEscrowPort loadReservationEscrowPort,
      LoadReservationActionStatePort loadReservationActionStatePort,
      LoadReservationWalletPort loadReservationWalletPort,
      Clock clock) {
    this.loadReservationPort = loadReservationPort;
    this.saveReservationPort = saveReservationPort;
    this.loadReservationEscrowPort = loadReservationEscrowPort;
    this.loadReservationActionStatePort = loadReservationActionStatePort;
    this.loadReservationWalletPort = loadReservationWalletPort;
    this.clock = clock;
  }

  public ApproveReservationService(
      LoadReservationPort loadReservationPort,
      SaveReservationPort saveReservationPort,
      LoadReservationEscrowPort loadReservationEscrowPort,
      LoadReservationActionStatePort loadReservationActionStatePort,
      Clock clock) {
    this(
        loadReservationPort,
        saveReservationPort,
        loadReservationEscrowPort,
        loadReservationActionStatePort,
        null,
        clock);
  }

  public void setTransactionPort(RunReservationTransactionPort transactionPort) {
    this.transactionPort = java.util.Objects.requireNonNull(transactionPort);
  }

  @Override
  public ApproveReservationResult execute(ApproveReservationCommand command) {
    return transactionPort.requiresNew(() -> executeLocked(command));
  }

  private ApproveReservationResult executeLocked(ApproveReservationCommand command) {
    log.debug(
        "ApproveReservation: reservationId={}, trainerId={}",
        command.reservationId(),
        command.authenticatedTrainerId());

    Reservation reservation =
        loadReservationPort
            .findByIdWithLock(command.reservationId())
            .orElseThrow(() -> new ReservationNotFoundException(command.reservationId()));

    if (!reservation.isOwnedByTrainer(command.authenticatedTrainerId())) {
      throw new MarketplaceUnauthorizedAccessException();
    }

    if (!reservation.getStatus().canTransitionTo(ReservationStatus.APPROVED)) {
      throw new MarketplaceReservationStateException(
          ErrorCode.MARKETPLACE_RESERVATION_INVALID_STATUS,
          "Cannot approve reservation in status: " + reservation.getStatus());
    }
    MarketplaceReservationEscrow escrow = null;
    if (reservation.getEffectiveEscrowFlow().isUserEip7702()) {
      escrow = loadEscrowProjection(reservation);
      validateNoActiveActionState(reservation);
      validateTrainerWalletSnapshot(command.authenticatedTrainerId(), reservation, escrow);
    }
    if ("ESCROW_DISPATCH_PENDING".equals(reservation.getTxHash())) {
      throw new MarketplaceReservationStateException(
          ErrorCode.MARKETPLACE_DEADLINE_SYNC_REQUIRED,
          "Cannot approve reservation while legacy escrow dispatch is unresolved");
    }
    requireBeforeContractDeadline(reservation, escrow);
    requireBeforeAutoCancelWindow(reservation);

    Reservation approved = reservation.approve();
    Reservation saved = saveReservationPort.save(approved);

    log.info(
        "Reservation approved: id={}, trainerId={}",
        saved.getId(),
        command.authenticatedTrainerId());
    return new ApproveReservationResult(
        saved.getId(),
        ReservationDisplayStatusMapper.displayStatus(saved),
        ReservationDisplayStatusMapper.businessStatus(saved));
  }

  private MarketplaceReservationEscrow loadEscrowProjection(Reservation reservation) {
    if (loadReservationEscrowPort == null) {
      if (reservation.getEffectiveEscrowStatus() != ReservationEscrowStatus.LOCKED) {
        throw new MarketplaceReservationStateException(
            ErrorCode.MARKETPLACE_RESERVATION_INVALID_STATUS,
            "Cannot approve reservation before purchase escrow is locked: "
                + reservation.getEffectiveEscrowStatus());
      }
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
    if (!escrow.getEscrowFlow().isUserEip7702()
        || escrow.getEscrowStatus() != ReservationEscrowStatus.LOCKED) {
      throw new MarketplaceReservationStateException(
          ErrorCode.MARKETPLACE_RESERVATION_INVALID_STATUS,
          "Cannot approve reservation before purchase escrow is locked: "
              + escrow.getEscrowStatus());
    }
    return escrow;
  }

  private void validateNoActiveActionState(Reservation reservation) {
    if (loadReservationActionStatePort == null) {
      return;
    }
    boolean active =
        !loadReservationActionStatePort
            .findByReservationIdAndStatuses(
                reservation.getId(),
                List.of(
                    ReservationActionStateStatus.PREPARING,
                    ReservationActionStateStatus.INTENT_BOUND))
            .isEmpty();
    if (active) {
      throw new MarketplaceReservationStateException(
          ErrorCode.MARKETPLACE_ACTIVE_EXECUTION_CONFLICT,
          "Cannot approve reservation while marketplace execution is active");
    }
  }

  private void validateTrainerWalletSnapshot(
      Long trainerId, Reservation reservation, MarketplaceReservationEscrow escrow) {
    if (loadReservationWalletPort == null) {
      return;
    }
    String trainerWallet =
        escrow == null ? reservation.getTrainerWalletAddress() : escrow.getTrainerWalletAddress();
    ReservationEscrowActionGuard.requireActiveWalletMatchesSnapshot(
        loadReservationWalletPort, trainerId, trainerWallet);
  }

  private void requireBeforeContractDeadline(
      Reservation reservation, MarketplaceReservationEscrow escrow) {
    if (!reservation.getEffectiveEscrowFlow().isUserEip7702()) {
      return;
    }
    LocalDateTime contractDeadlineAt =
        escrow == null ? reservation.getContractDeadlineAt() : escrow.getContractDeadlineAt();
    if (contractDeadlineAt != null && LocalDateTime.now(clock).isAfter(contractDeadlineAt)) {
      throw new MarketplaceReservationStateException(
          ErrorCode.MARKETPLACE_DEADLINE_REFUND_REQUIRED,
          "Contract deadline expired; marketplace approve is no longer allowed");
    }
  }

  private void requireBeforeAutoCancelWindow(Reservation reservation) {
    LocalDateTime now = LocalDateTime.now(clock);
    if (reservation.getCreatedAt() != null
        && !reservation.getCreatedAt().isAfter(now.minusHours(APPROVE_TIMEOUT_HOURS))) {
      throw new MarketplaceReservationStateException(
          ErrorCode.MARKETPLACE_RESERVATION_INVALID_STATUS,
          "Cannot approve reservation after the trainer approval timeout window");
    }
    LocalDateTime sessionStartAt =
        LocalDateTime.of(reservation.getReservationDate(), reservation.getReservationTime());
    if (!sessionStartAt.isAfter(now.plusHours(SESSION_START_GUARD_HOURS))) {
      throw new MarketplaceReservationStateException(
          ErrorCode.MARKETPLACE_RESERVATION_INVALID_STATUS,
          "Cannot approve reservation within the auto-cancel session guard window");
    }
  }
}
