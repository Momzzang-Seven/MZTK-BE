package momzzangseven.mztkbe.modules.web3.treasury.infrastructure.persistence.repository;

import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.treasury.infrastructure.persistence.entity.Web3TreasuryKeyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface Web3TreasuryKeyJpaRepository extends JpaRepository<Web3TreasuryKeyEntity, Long> {

  Optional<Web3TreasuryKeyEntity> findByWalletAlias(String walletAlias);
}
