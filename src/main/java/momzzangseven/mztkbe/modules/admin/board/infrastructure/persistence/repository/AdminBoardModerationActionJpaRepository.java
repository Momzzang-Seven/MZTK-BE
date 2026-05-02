package momzzangseven.mztkbe.modules.admin.board.infrastructure.persistence.repository;

import momzzangseven.mztkbe.modules.admin.board.infrastructure.persistence.entity.AdminBoardModerationActionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminBoardModerationActionJpaRepository
    extends JpaRepository<AdminBoardModerationActionEntity, Long> {}
