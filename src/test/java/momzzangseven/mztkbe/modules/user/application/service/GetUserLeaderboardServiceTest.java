package momzzangseven.mztkbe.modules.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.util.List;
import momzzangseven.mztkbe.modules.user.application.dto.GetUserLeaderboardResult;
import momzzangseven.mztkbe.modules.user.application.dto.LeaderboardUserSnapshot;
import momzzangseven.mztkbe.modules.user.application.port.out.LoadUserLeaderboardPort;
import momzzangseven.mztkbe.modules.user.domain.model.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("GetUserLeaderboardService 단위 테스트")
class GetUserLeaderboardServiceTest {

  @Mock private LoadUserLeaderboardPort loadUserLeaderboardPort;

  @InjectMocks private GetUserLeaderboardService service;

  @Test
  @DisplayName("리더보드 조회 시 응답 순서대로 rank 1부터 부여한다")
  void execute_assignsSequentialRanks() {
    given(
            loadUserLeaderboardPort.loadTopLeaderboardUsers(
                List.of(UserRole.USER, UserRole.TRAINER), 10))
        .willReturn(
            List.of(
                new LeaderboardUserSnapshot(2L, "b", null, 5, 300, 20),
                new LeaderboardUserSnapshot(7L, "c", "https://img", 4, 250, 10),
                new LeaderboardUserSnapshot(9L, "d", null, 1, 0, 0)));

    GetUserLeaderboardResult result = service.execute();

    assertThat(result.users()).hasSize(3);
    assertThat(result.users().get(0).rank()).isEqualTo(1);
    assertThat(result.users().get(0).userId()).isEqualTo(2L);
    assertThat(result.users().get(1).rank()).isEqualTo(2);
    assertThat(result.users().get(2).rank()).isEqualTo(3);
    verify(loadUserLeaderboardPort)
        .loadTopLeaderboardUsers(List.of(UserRole.USER, UserRole.TRAINER), 10);
  }

  @Test
  @DisplayName("조회 결과가 10명 미만이면 전체를 그대로 반환한다")
  void execute_returnsAllRowsWhenLessThanLimit() {
    given(
            loadUserLeaderboardPort.loadTopLeaderboardUsers(
                List.of(UserRole.USER, UserRole.TRAINER), 10))
        .willReturn(List.of(new LeaderboardUserSnapshot(11L, "solo", null, 1, 0, 0)));

    GetUserLeaderboardResult result = service.execute();

    assertThat(result.users())
        .singleElement()
        .satisfies(user -> assertThat(user.rank()).isEqualTo(1));
    assertThat(result.users().get(0).lifetimeXp()).isEqualTo(0);
  }
}
