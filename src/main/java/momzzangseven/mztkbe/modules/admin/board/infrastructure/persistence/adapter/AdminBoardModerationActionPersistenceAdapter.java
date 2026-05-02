package momzzangseven.mztkbe.modules.admin.board.infrastructure.persistence.adapter;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.admin.board.application.port.out.SaveAdminBoardModerationActionPort;
import momzzangseven.mztkbe.modules.admin.board.domain.model.AdminBoardModerationAction;
import momzzangseven.mztkbe.modules.admin.board.infrastructure.persistence.entity.AdminBoardModerationActionEntity;
import momzzangseven.mztkbe.modules.admin.board.infrastructure.persistence.repository.AdminBoardModerationActionJpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class AdminBoardModerationActionPersistenceAdapter
    implements SaveAdminBoardModerationActionPort {

  private final AdminBoardModerationActionJpaRepository repository;

  @Override
  @Transactional
  public AdminBoardModerationAction save(AdminBoardModerationAction action) {
    AdminBoardModerationActionEntity saved =
        repository.save(AdminBoardModerationActionEntity.from(action));
    return saved.toDomain();
  }
}
