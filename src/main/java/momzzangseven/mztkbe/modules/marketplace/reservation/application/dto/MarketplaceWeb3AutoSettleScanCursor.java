package momzzangseven.mztkbe.modules.marketplace.reservation.application.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;

public record MarketplaceWeb3AutoSettleScanCursor(
    LocalDate reservationDate, LocalTime reservationTime, Long reservationId) {

  public static MarketplaceWeb3AutoSettleScanCursor empty() {
    return new MarketplaceWeb3AutoSettleScanCursor(null, null, null);
  }

  public MarketplaceWeb3AutoSettleScanCursor {
    boolean allNull = reservationDate == null && reservationTime == null && reservationId == null;
    boolean allPresent =
        reservationDate != null && reservationTime != null && reservationId != null;
    if (!allNull && !allPresent) {
      throw new Web3InvalidInputException("scan cursor must be fully populated or empty");
    }
    if (reservationId != null && reservationId <= 0) {
      throw new Web3InvalidInputException("cursor reservationId must be positive");
    }
  }
}
