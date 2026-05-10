package momzzangseven.mztkbe.modules.admin.dashboard.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.util.Map;
import momzzangseven.mztkbe.modules.admin.dashboard.application.dto.AdminUserStatsResult;
import momzzangseven.mztkbe.modules.admin.dashboard.application.port.out.LoadAdminUserStatsPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("GetAdminUserStatsService 단위 테스트")
class GetAdminUserStatsServiceTest {

  @Mock private LoadAdminUserStatsPort loadAdminUserStatsPort;

  @InjectMocks private GetAdminUserStatsService service;

  @Test
  @DisplayName("admin 대시보드 사용자 통계를 그대로 반환한다")
  void execute_returnsDashboardUserStats() {
    given(loadAdminUserStatsPort.load())
        .willReturn(
            new LoadAdminUserStatsPort.AdminUserStatsView(
                15L, 11L, 3L, Map.of("USER", 9L, "TRAINER", 2L)));

    AdminUserStatsResult result = service.execute(99L);

    assertThat(result.totalUserCount()).isEqualTo(15L);
    assertThat(result.activeUserCount()).isEqualTo(11L);
    assertThat(result.blockedUserCount()).isEqualTo(3L);
    assertThat(result.roleCounts()).containsEntry("USER", 9L).containsEntry("TRAINER", 2L);
  }
}
