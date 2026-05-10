package momzzangseven.mztkbe.modules.admin.board.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import java.time.LocalDateTime;
import java.util.List;
import momzzangseven.mztkbe.modules.admin.board.domain.model.AdminBoardModerationAction;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardModerationExecutionMode;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardModerationReasonCode;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardModerationTargetFlowType;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardModerationTargetType;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardType;
import momzzangseven.mztkbe.modules.admin.board.infrastructure.persistence.entity.AdminBoardModerationActionEntity;
import momzzangseven.mztkbe.modules.admin.board.infrastructure.persistence.repository.AdminBoardModerationActionJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminBoardModerationActionPersistenceAdapter 단위 테스트")
class AdminBoardModerationActionPersistenceAdapterTest {

  @Mock private AdminBoardModerationActionJpaRepository repository;

  @InjectMocks private AdminBoardModerationActionPersistenceAdapter adapter;

  @Test
  @DisplayName("save()는 adapter 내부 mapper로 domain/entity 변환을 수행한다")
  void save_mapsDomainAndEntityInAdapter() {
    AdminBoardModerationAction action =
        AdminBoardModerationAction.create(
            9L,
            AdminBoardModerationTargetType.COMMENT,
            31L,
            21L,
            AdminBoardType.FREE,
            AdminBoardModerationReasonCode.SPAM,
            "ad",
            AdminBoardModerationTargetFlowType.STANDARD,
            AdminBoardModerationExecutionMode.SOFT_DELETE,
            LocalDateTime.parse("2026-05-04T00:00:00"));
    given(repository.save(any(AdminBoardModerationActionEntity.class)))
        .willAnswer(
            invocation -> {
              AdminBoardModerationActionEntity entity = invocation.getArgument(0);
              return AdminBoardModerationActionEntity.builder()
                  .id(100L)
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
            });

    AdminBoardModerationAction saved = adapter.save(action);

    assertThat(saved.getId()).isEqualTo(100L);
    assertThat(saved.getOperatorId()).isEqualTo(9L);
    assertThat(saved.getTargetType()).isEqualTo(AdminBoardModerationTargetType.COMMENT);
    assertThat(saved.getReasonCode()).isEqualTo(AdminBoardModerationReasonCode.SPAM);
  }

  @Test
  @DisplayName("admin_board_moderation_actions 집계 projection을 dashboard stats view로 변환한다")
  void load_returnsModerationStats() {
    given(repository.countByReasonCode())
        .willReturn(List.of(reason(AdminBoardModerationReasonCode.SPAM, 2L)));
    given(repository.countByBoardType()).willReturn(List.of(board(AdminBoardType.FREE, 2L)));
    given(repository.countByTargetType())
        .willReturn(List.of(target(AdminBoardModerationTargetType.COMMENT, 2L)));

    var result = adapter.load();

    assertThat(result.reasonCodeCounts()).containsEntry(AdminBoardModerationReasonCode.SPAM, 2L);
    assertThat(result.boardTypeCounts()).containsEntry(AdminBoardType.FREE, 2L);
    assertThat(result.targetTypeCounts()).containsEntry(AdminBoardModerationTargetType.COMMENT, 2L);
  }

  private AdminBoardModerationActionJpaRepository.ReasonCodeCount reason(
      AdminBoardModerationReasonCode reasonCode, Long count) {
    return new AdminBoardModerationActionJpaRepository.ReasonCodeCount() {
      @Override
      public AdminBoardModerationReasonCode getReasonCode() {
        return reasonCode;
      }

      @Override
      public Long getActionCount() {
        return count;
      }
    };
  }

  private AdminBoardModerationActionJpaRepository.BoardTypeCount board(
      AdminBoardType boardType, Long count) {
    return new AdminBoardModerationActionJpaRepository.BoardTypeCount() {
      @Override
      public AdminBoardType getBoardType() {
        return boardType;
      }

      @Override
      public Long getActionCount() {
        return count;
      }
    };
  }

  private AdminBoardModerationActionJpaRepository.TargetTypeCount target(
      AdminBoardModerationTargetType targetType, Long count) {
    return new AdminBoardModerationActionJpaRepository.TargetTypeCount() {
      @Override
      public AdminBoardModerationTargetType getTargetType() {
        return targetType;
      }

      @Override
      public Long getActionCount() {
        return count;
      }
    };
  }
}
