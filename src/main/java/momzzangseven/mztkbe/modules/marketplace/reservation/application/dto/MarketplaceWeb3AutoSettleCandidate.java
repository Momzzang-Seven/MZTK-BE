package momzzangseven.mztkbe.modules.marketplace.reservation.application.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

public record MarketplaceWeb3AutoSettleCandidate(
    Long reservationId,
    String orderKey,
    LocalDate reservationDate,
    LocalTime reservationTime,
    int durationMinutes,
    LocalDateTime contractDeadlineAt) {

  public MarketplaceWeb3AutoSettleCandidate {
    if (reservationId == null || reservationId <= 0) {
      throw new Web3InvalidInputException("reservationId must be positive");
    }
    if (orderKey == null || orderKey.isBlank()) {
      throw new Web3InvalidInputException("orderKey is required");
    }
    if (reservationDate == null || reservationTime == null) {
      throw new Web3InvalidInputException("reservationDate and reservationTime are required");
    }
    if (durationMinutes <= 0) {
      throw new Web3InvalidInputException("durationMinutes must be positive");
    }
    orderKey = orderKey.trim();
  }

  public LocalDateTime sessionEndAt() {
    return LocalDateTime.of(reservationDate, reservationTime).plusMinutes(durationMinutes);
  }
}
