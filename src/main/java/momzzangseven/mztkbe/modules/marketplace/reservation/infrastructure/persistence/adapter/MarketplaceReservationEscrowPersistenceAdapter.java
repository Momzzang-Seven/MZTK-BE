package momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.persistence.adapter;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadReservationEscrowPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SaveReservationEscrowPort;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.model.MarketplaceReservationEscrow;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowFlow;
import momzzangseven.mztkbe.modules.marketplace.reservation.domain.vo.ReservationEscrowStatus;
import momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.persistence.entity.MarketplaceReservationEscrowEntity;
import momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.persistence.repository.MarketplaceReservationEscrowJpaRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MarketplaceReservationEscrowPersistenceAdapter
    implements LoadReservationEscrowPort, SaveReservationEscrowPort {

  private static final BigInteger MAX_UINT256 =
      BigInteger.ONE.shiftLeft(256).subtract(BigInteger.ONE);

  private final MarketplaceReservationEscrowJpaRepository repository;

  @Override
  public Optional<MarketplaceReservationEscrow> findByReservationId(Long reservationId) {
    return repository.findByReservationId(reservationId).map(this::toDomain);
  }

  @Override
  public Optional<MarketplaceReservationEscrow> findByReservationIdWithLock(Long reservationId) {
    return repository.findByReservationIdWithLock(reservationId).map(this::toDomain);
  }

  @Override
  public Optional<MarketplaceReservationEscrow> findByOrderKey(String orderKey) {
    return repository.findByOrderKey(orderKey).map(this::toDomain);
  }

  @Override
  public MarketplaceReservationEscrow save(MarketplaceReservationEscrow escrow) {
    return toDomain(repository.save(toEntity(escrow)));
  }

  private MarketplaceReservationEscrow toDomain(MarketplaceReservationEscrowEntity entity) {
    return MarketplaceReservationEscrow.builder()
        .id(entity.getId())
        .reservationId(entity.getReservationId())
        .escrowFlow(ReservationEscrowFlow.valueOf(entity.getEscrowFlow()))
        .escrowStatus(ReservationEscrowStatus.valueOf(entity.getEscrowStatus()))
        .orderKey(entity.getOrderKey())
        .buyerWalletAddress(entity.getBuyerWalletAddress())
        .trainerWalletAddress(entity.getTrainerWalletAddress())
        .tokenAddress(entity.getTokenAddress())
        .priceBaseUnits(toBigInteger(entity.getPriceBaseUnits()))
        .holdExpiresAt(entity.getHoldExpiresAt())
        .expectedContractDeadlineEpochSeconds(entity.getExpectedContractDeadlineEpochSeconds())
        .expectedContractDeadlineAt(entity.getExpectedContractDeadlineAt())
        .contractDeadlineEpochSeconds(entity.getContractDeadlineEpochSeconds())
        .contractDeadlineAt(entity.getContractDeadlineAt())
        .lastChainState(entity.getLastChainState())
        .lastChainSyncedAt(entity.getLastChainSyncedAt())
        .lastTxHash(entity.getLastTxHash())
        .lastFailureCode(entity.getLastFailureCode())
        .lastFailureMessage(entity.getLastFailureMessage())
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .build();
  }

  private MarketplaceReservationEscrowEntity toEntity(MarketplaceReservationEscrow domain) {
    return MarketplaceReservationEscrowEntity.builder()
        .id(domain.getId())
        .reservationId(domain.getReservationId())
        .escrowFlow(domain.getEscrowFlow().name())
        .escrowStatus(domain.getEscrowStatus().name())
        .orderKey(domain.getOrderKey())
        .buyerWalletAddress(domain.getBuyerWalletAddress())
        .trainerWalletAddress(domain.getTrainerWalletAddress())
        .tokenAddress(domain.getTokenAddress())
        .priceBaseUnits(toBigDecimal(domain.getPriceBaseUnits()))
        .holdExpiresAt(domain.getHoldExpiresAt())
        .expectedContractDeadlineEpochSeconds(domain.getExpectedContractDeadlineEpochSeconds())
        .expectedContractDeadlineAt(domain.getExpectedContractDeadlineAt())
        .contractDeadlineEpochSeconds(domain.getContractDeadlineEpochSeconds())
        .contractDeadlineAt(domain.getContractDeadlineAt())
        .lastChainState(domain.getLastChainState())
        .lastChainSyncedAt(domain.getLastChainSyncedAt())
        .lastTxHash(domain.getLastTxHash())
        .lastFailureCode(domain.getLastFailureCode())
        .lastFailureMessage(domain.getLastFailureMessage())
        .createdAt(domain.getCreatedAt())
        .updatedAt(domain.getUpdatedAt())
        .build();
  }

  private static BigInteger toBigInteger(BigDecimal value) {
    return value == null ? null : value.toBigIntegerExact();
  }

  private static BigDecimal toBigDecimal(BigInteger value) {
    if (value == null) {
      return null;
    }
    if (value.signum() < 0 || value.compareTo(MAX_UINT256) > 0) {
      throw new IllegalArgumentException("marketplace escrow priceBaseUnits must fit uint256");
    }
    return new BigDecimal(value);
  }
}
