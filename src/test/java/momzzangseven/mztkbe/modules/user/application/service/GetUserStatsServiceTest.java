package momzzangseven.mztkbe.modules.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.util.List;
import momzzangseven.mztkbe.modules.user.application.dto.GetUserStatsResult;
import momzzangseven.mztkbe.modules.user.application.port.out.LoadUserStatsPort;
import momzzangseven.mztkbe.modules.user.domain.model.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("GetUserStatsService 단위 테스트")
class GetUserStatsServiceTest {

  @Mock private LoadUserStatsPort loadUserStatsPort;

  @InjectMocks private GetUserStatsService service;

  @Test
  @DisplayName("USER/TRAINER roleCounts 를 0 기본값 포함으로 반환한다")
  void execute_returnsRoleCountsWithDefaults() {
    given(loadUserStatsPort.loadUserStatusCounts())
        .willReturn(new LoadUserStatsPort.UserStatusCounts(12L, 9L, 2L));
    given(loadUserStatsPort.loadRoleCounts(List.of(UserRole.USER, UserRole.TRAINER)))
        .willReturn(List.of(new LoadUserStatsPort.UserRoleCount(UserRole.USER, 7L)));

    GetUserStatsResult result = service.execute();

    assertThat(result.totalUserCount()).isEqualTo(12L);
    assertThat(result.activeUserCount()).isEqualTo(9L);
    assertThat(result.blockedUserCount()).isEqualTo(2L);
    assertThat(result.roleCounts()).containsEntry(UserRole.USER, 7L);
    assertThat(result.roleCounts()).containsEntry(UserRole.TRAINER, 0L);
    assertThat(result.roleCounts()).doesNotContainKey(UserRole.ADMIN);
  }
}
