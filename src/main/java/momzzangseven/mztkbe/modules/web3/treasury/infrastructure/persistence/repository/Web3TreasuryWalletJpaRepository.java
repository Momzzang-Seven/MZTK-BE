package momzzangseven.mztkbe.modules.web3.treasury.infrastructure.persistence.repository;

import jakarta.persistence.LockModeType;
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
   * Acquire a {@code PESSIMISTIC_WRITE} row lock on the wallet row bound to {@code walletAlias}.
   * Mirrors the pattern established by {@code PostJpaRepository.findByIdForUpdate}. The lock is
   * held for the duration of the surrounding {@code @Transactional} call.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT w FROM Web3TreasuryWalletEntity w WHERE w.walletAlias = :alias")
  Optional<Web3TreasuryWalletEntity> findByWalletAliasForUpdate(@Param("alias") String alias);
}
