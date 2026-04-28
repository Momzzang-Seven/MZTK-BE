package momzzangseven.mztkbe.modules.web3.treasury.infrastructure.persistence.repository;

import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.treasury.infrastructure.persistence.entity.Web3TreasuryWalletEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface Web3TreasuryWalletJpaRepository
    extends JpaRepository<Web3TreasuryWalletEntity, Long> {

  Optional<Web3TreasuryWalletEntity> findByWalletAlias(String walletAlias);
}
