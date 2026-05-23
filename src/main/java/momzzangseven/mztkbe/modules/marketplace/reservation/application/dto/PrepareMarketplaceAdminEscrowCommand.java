package momzzangseven.mztkbe.modules.marketplace.reservation.application.dto;

import java.math.BigInteger;
import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationActionRequestSource;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowAction;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationStatus;

/** Reservation-owned snapshot used to prepare a marketplace admin Web3 execution. */
public record PrepareMarketplaceAdminEscrowCommand(
    ReservationEscrowAction actionType,
    Long reservationId,
    String orderId,
    String orderKey,
    Long requesterUserId,
    Long counterpartyUserId,
    Long buyerUserId,
    Long trainerUserId,
    Long reservationVersion,
    ReservationStatus expectedReservationStatus,
    ReservationEscrowStatus expectedEscrowStatus,
    String buyerWalletAddress,
    String trainerWalletAddress,
    String tokenAddress,
    BigInteger priceBaseUnits,
    Integer bookedPriceAmountKrw,
    LocalDateTime sessionEndAt,
    String pendingAttemptToken,
    String targetTerminalStatus,
    Long escrowId,
    Long actionStateId,
    ReservationActionRequestSource requestSource,
    Long operatorUserId,
    String schedulerRunId,
    String reasonCode,
    String memo,
    String rootIdempotencyKey) {}
