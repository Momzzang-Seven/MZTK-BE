package momzzangseven.mztkbe.modules.admin.dashboard.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.util.Map;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardModerationReasonCode;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardModerationTargetType;
import momzzangseven.mztkbe.modules.admin.board.domain.vo.AdminBoardType;
import momzzangseven.mztkbe.modules.admin.dashboard.application.port.out.LoadAdminBoardStatsPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("GetAdminBoardStatsService 단위 테스트")
class GetAdminBoardStatsServiceTest {

  @Mock private LoadAdminBoardStatsPort loadAdminBoardStatsPort;

  @InjectMocks private GetAdminBoardStatsService service;

  @Test
  @DisplayName("데이터가 없어도 reasonCode, boardType, targetType 전체 키를 0으로 반환한다")
  void execute_emptyStats_returnsZeroFilledMaps() {
    given(loadAdminBoardStatsPort.load())
        .willReturn(new LoadAdminBoardStatsPort.AdminBoardStatsView(Map.of(), Map.of(), Map.of()));

    var result = service.execute(9L);

    assertThat(result.postRemovalReasonStats())
        .containsEntry("INAPPROPRIATE", 0L)
        .containsEntry("SPAM", 0L)
        .containsEntry("POLICY_VIOLATION", 0L)
        .containsEntry("HARASSMENT", 0L)
        .containsEntry("OTHER", 0L);
    assertThat(result.boardTypeSplit()).containsEntry("FREE", 0L).containsEntry("QUESTION", 0L);
    assertThat(result.targetTypeStats()).containsEntry("POST", 0L).containsEntry("COMMENT", 0L);
  }

  @Test
  @DisplayName("reasonCode, boardType, targetType 통계 값을 enum 이름 기반으로 반환한다")
  void execute_stats_returnsCounts() {
    given(loadAdminBoardStatsPort.load())
        .willReturn(
            new LoadAdminBoardStatsPort.AdminBoardStatsView(
                Map.of(
                    AdminBoardModerationReasonCode.SPAM,
                    3L,
                    AdminBoardModerationReasonCode.POLICY_VIOLATION,
                    2L),
                Map.of(AdminBoardType.FREE, 4L, AdminBoardType.QUESTION, 1L),
                Map.of(
                    AdminBoardModerationTargetType.POST,
                    1L,
                    AdminBoardModerationTargetType.COMMENT,
                    4L)));

    var result = service.execute(9L);

    assertThat(result.postRemovalReasonStats())
        .containsEntry("SPAM", 3L)
        .containsEntry("POLICY_VIOLATION", 2L)
        .containsEntry("OTHER", 0L);
    assertThat(result.boardTypeSplit()).containsEntry("FREE", 4L).containsEntry("QUESTION", 1L);
    assertThat(result.targetTypeStats()).containsEntry("POST", 1L).containsEntry("COMMENT", 4L);
  }
}
