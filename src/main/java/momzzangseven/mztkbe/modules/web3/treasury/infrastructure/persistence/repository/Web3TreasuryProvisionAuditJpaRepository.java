package momzzangseven.mztkbe.modules.web3.treasury.infrastructure.persistence.repository;

import momzzangseven.mztkbe.modules.web3.treasury.infrastructure.persistence.entity.Web3TreasuryProvisionAuditEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface Web3TreasuryProvisionAuditJpaRepository
    extends JpaRepository<Web3TreasuryProvisionAuditEntity, Long> {}
