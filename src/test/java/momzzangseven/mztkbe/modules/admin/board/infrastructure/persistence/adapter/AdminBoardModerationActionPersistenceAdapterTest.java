package momzzangseven.mztkbe.modules.admin.board.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.util.List;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardModerationReasonCode;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardModerationTargetType;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardType;
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
