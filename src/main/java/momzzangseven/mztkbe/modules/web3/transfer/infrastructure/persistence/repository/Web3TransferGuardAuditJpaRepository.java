package momzzangseven.mztkbe.modules.web3.transfer.infrastructure.persistence.repository;

import momzzangseven.mztkbe.modules.web3.transfer.infrastructure.persistence.entity.Web3TransferGuardAuditEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface Web3TransferGuardAuditJpaRepository
    extends JpaRepository<Web3TransferGuardAuditEntity, Long> {}
