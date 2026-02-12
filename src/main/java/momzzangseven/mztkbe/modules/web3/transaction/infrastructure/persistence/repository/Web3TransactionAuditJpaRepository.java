package momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.repository;

import java.util.List;
import momzzangseven.mztkbe.modules.web3.transaction.infrastructure.persistence.entity.Web3TransactionAuditEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface Web3TransactionAuditJpaRepository
    extends JpaRepository<Web3TransactionAuditEntity, Long> {

  List<Web3TransactionAuditEntity> findByWeb3TransactionIdOrderByCreatedAtDesc(
      Long web3TransactionId);
}
