package momzzangseven.mztkbe.modules.web3.admin.audit.infrastructure.persistence.repository;

import java.util.List;
import momzzangseven.mztkbe.modules.web3.admin.audit.infrastructure.persistence.entity.Web3AdminActionAuditEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface Web3AdminActionAuditJpaRepository
    extends JpaRepository<Web3AdminActionAuditEntity, Long> {

  List<Web3AdminActionAuditEntity> findByActionTypeOrderByCreatedAtDesc(String actionType);
}
