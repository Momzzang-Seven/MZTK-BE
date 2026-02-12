package momzzangseven.mztkbe.modules.web3.token.infrastructure.persistence.repository;

import momzzangseven.mztkbe.modules.web3.token.infrastructure.persistence.entity.Web3TreasuryKeyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface Web3TreasuryKeyJpaRepository extends JpaRepository<Web3TreasuryKeyEntity, Short> {}
