package momzzangseven.mztkbe.modules.marketplace.reservation.domain.model;

import java.math.BigInteger;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowFlow;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowStatus;

/** Split on-chain escrow projection for a marketplace reservation. */
@Getter
@Builder(toBuilder = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class MarketplaceReservationEscrow {

  private final Long id;
  private final Long reservationId;
  private final ReservationEscrowFlow escrowFlow;
  private final ReservationEscrowStatus escrowStatus;
  private final String orderKey;
  private final String buyerWalletAddress;
  private final String trainerWalletAddress;
  private final String tokenAddress;
  private final BigInteger priceBaseUnits;
  private final LocalDateTime holdExpiresAt;
  private final Long expectedContractDeadlineEpochSeconds;
  private final LocalDateTime expectedContractDeadlineAt;
  private final Long contractDeadlineEpochSeconds;
  private final LocalDateTime contractDeadlineAt;
  private final Integer lastChainState;
  private final LocalDateTime lastChainSyncedAt;
  private final String lastTxHash;
  private final String lastFailureCode;
  private final String lastFailureMessage;
  private final LocalDateTime createdAt;
  private final LocalDateTime updatedAt;
}
