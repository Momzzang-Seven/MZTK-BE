package momzzangseven.mztkbe.modules.web3.treasury.infrastructure.persistence.repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.treasury.infrastructure.persistence.entity.Web3TreasuryWalletEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface Web3TreasuryWalletJpaRepository
    extends JpaRepository<Web3TreasuryWalletEntity, Long> {

  Optional<Web3TreasuryWalletEntity> findByWalletAlias(String walletAlias);

  /**
   * Loads every row sharing {@code treasuryAddress} — the full cohort for that address. Read-only;
   * use {@link #findAllByTreasuryAddressForUpdate(String)} when the caller will transition the
   * cohort.
   */
  List<Web3TreasuryWalletEntity> findAllByTreasuryAddress(String treasuryAddress);

  /**
   * Loads the full cohort for {@code treasuryAddress} under a {@code SELECT ... FOR UPDATE} write
   * lock so concurrent cohort transitions serialize on the same rows.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT w FROM Web3TreasuryWalletEntity w WHERE w.treasuryAddress = :treasuryAddress")
  List<Web3TreasuryWalletEntity> findAllByTreasuryAddressForUpdate(
      @Param("treasuryAddress") String treasuryAddress);
}
