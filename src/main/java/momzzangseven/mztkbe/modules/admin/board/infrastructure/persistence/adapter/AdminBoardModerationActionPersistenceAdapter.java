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
    AdminBoardModerationActionEntity saved = repository.save(toEntity(action));
    return toDomain(saved);
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

  private AdminBoardModerationActionEntity toEntity(AdminBoardModerationAction action) {
    return AdminBoardModerationActionEntity.builder()
        .id(action.getId())
        .operatorId(action.getOperatorId())
        .targetType(action.getTargetType())
        .targetId(action.getTargetId())
        .postId(action.getPostId())
        .boardType(action.getBoardType())
        .reasonCode(action.getReasonCode())
        .reasonDetail(action.getReasonDetail())
        .targetFlowType(action.getTargetFlowType())
        .executionMode(action.getExecutionMode())
        .createdAt(action.getCreatedAt())
        .build();
  }

  private AdminBoardModerationAction toDomain(AdminBoardModerationActionEntity entity) {
    return AdminBoardModerationAction.builder()
        .id(entity.getId())
        .operatorId(entity.getOperatorId())
        .targetType(entity.getTargetType())
        .targetId(entity.getTargetId())
        .postId(entity.getPostId())
        .boardType(entity.getBoardType())
        .reasonCode(entity.getReasonCode())
        .reasonDetail(entity.getReasonDetail())
        .targetFlowType(entity.getTargetFlowType())
        .executionMode(entity.getExecutionMode())
        .createdAt(entity.getCreatedAt())
        .build();
  }
}
