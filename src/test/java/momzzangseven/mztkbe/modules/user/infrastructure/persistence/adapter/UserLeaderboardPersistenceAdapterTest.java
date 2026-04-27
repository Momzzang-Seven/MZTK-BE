package momzzangseven.mztkbe.modules.user.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.util.List;
import momzzangseven.mztkbe.modules.user.domain.model.UserRole;
import momzzangseven.mztkbe.modules.user.infrastructure.persistence.repository.UserLeaderboardProjection;
import momzzangseven.mztkbe.modules.user.infrastructure.persistence.repository.UserLeaderboardQueryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserLeaderboardPersistenceAdapter 단위 테스트")
class UserLeaderboardPersistenceAdapterTest {

  @Mock private UserLeaderboardQueryRepository userLeaderboardQueryRepository;

  @InjectMocks private UserLeaderboardPersistenceAdapter adapter;

  @Test
  @DisplayName("리더보드 projection을 application snapshot으로 매핑한다")
  void loadTopLeaderboardUsers_mapsProjectionToSnapshot() {
    given(
            userLeaderboardQueryRepository.findTopLeaderboardUsers(
                List.of(UserRole.USER, UserRole.TRAINER), Pageable.ofSize(10)))
        .willReturn(List.of(new UserLeaderboardProjection(3L, "nick", "https://img", 7, 500, 25)));

    var result = adapter.loadTopLeaderboardUsers(List.of(UserRole.USER, UserRole.TRAINER), 10);

    assertThat(result)
        .singleElement()
        .satisfies(
            user -> {
              assertThat(user.userId()).isEqualTo(3L);
              assertThat(user.level()).isEqualTo(7);
              assertThat(user.lifetimeXp()).isEqualTo(500);
              assertThat(user.availableXp()).isEqualTo(25);
            });

    ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
    verify(userLeaderboardQueryRepository)
        .findTopLeaderboardUsers(
            org.mockito.ArgumentMatchers.eq(List.of(UserRole.USER, UserRole.TRAINER)),
            pageableCaptor.capture());
    assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(0);
    assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(10);
  }

  @Test
  @DisplayName("limit이 0 이하면 예외를 던진다")
  void loadTopLeaderboardUsers_invalidLimit_throws() {
    assertThatThrownBy(() -> adapter.loadTopLeaderboardUsers(List.of(UserRole.USER), 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("limit must be > 0");
  }
}
