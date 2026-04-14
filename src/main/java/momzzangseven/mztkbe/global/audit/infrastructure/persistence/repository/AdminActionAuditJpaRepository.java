package momzzangseven.mztkbe.global.audit.infrastructure.persistence.repository;

import momzzangseven.mztkbe.global.audit.infrastructure.persistence.entity.AdminActionAuditEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminActionAuditJpaRepository
    extends JpaRepository<AdminActionAuditEntity, Long> {}
