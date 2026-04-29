package momzzangseven.mztkbe.modules.web3.treasury.infrastructure.persistence.repository;

import momzzangseven.mztkbe.modules.web3.treasury.infrastructure.persistence.entity.Web3TreasuryKmsAuditEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface Web3TreasuryKmsAuditJpaRepository
    extends JpaRepository<Web3TreasuryKmsAuditEntity, Long> {}
