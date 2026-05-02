package momzzangseven.mztkbe.modules.admin.board.infrastructure.persistence.adapter;

import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.admin.board.application.port.out.SaveAdminBoardModerationActionPort;
import momzzangseven.mztkbe.modules.admin.board.domain.model.AdminBoardModerationAction;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardModerationReasonCode;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardModerationTargetType;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardType;
import momzzangseven.mztkbe.modules.admin.board.infrastructure.persistence.entity.AdminBoardModerationActionEntity;
import momzzangseven.mztkbe.modules.admin.board.infrastructure.persistence.repository.AdminBoardModerationActionJpaRepository;
import momzzangseven.mztkbe.modules.admin.dashboard.application.port.out.LoadAdminBoardStatsPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class AdminBoardModerationActionPersistenceAdapter
    implements SaveAdminBoardModerationActionPort, LoadAdminBoardStatsPort {

  private final AdminBoardModerationActionJpaRepository repository;

  @Override
  @Transactional
  public AdminBoardModerationAction save(AdminBoardModerationAction action) {
    AdminBoardModerationActionEntity saved =
        repository.save(AdminBoardModerationActionEntity.from(action));
    return saved.toDomain();
  }

  @Override
  @Transactional(readOnly = true)
  public AdminBoardStatsView load() {
    return new AdminBoardStatsView(
        loadReasonCodeCounts(), loadBoardTypeCounts(), loadTargetTypeCounts());
  }

  private Map<AdminBoardModerationReasonCode, Long> loadReasonCodeCounts() {
    return repository.countByReasonCode().stream()
        .collect(
            Collectors.toMap(
                AdminBoardModerationActionJpaRepository.ReasonCodeCount::getReasonCode,
                AdminBoardModerationActionJpaRepository.ReasonCodeCount::getActionCount));
  }

  private Map<AdminBoardType, Long> loadBoardTypeCounts() {
    return repository.countByBoardType().stream()
        .collect(
            Collectors.toMap(
                AdminBoardModerationActionJpaRepository.BoardTypeCount::getBoardType,
                AdminBoardModerationActionJpaRepository.BoardTypeCount::getActionCount));
  }

  private Map<AdminBoardModerationTargetType, Long> loadTargetTypeCounts() {
    return repository.countByTargetType().stream()
        .collect(
            Collectors.toMap(
                AdminBoardModerationActionJpaRepository.TargetTypeCount::getTargetType,
                AdminBoardModerationActionJpaRepository.TargetTypeCount::getActionCount));
  }
}
