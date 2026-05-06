package momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.external.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.LoadUserSummaryPort.UserSummary;
import momzzangseven.mztkbe.modules.user.application.dto.UserInfo;
import momzzangseven.mztkbe.modules.user.application.port.in.LoadUserInfoUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserSummaryAdapterTest {

  @Mock private LoadUserInfoUseCase loadUserInfoUseCase;

  @InjectMocks private UserSummaryAdapter sut;

  private UserInfo userInfo(Long id, String nickname) {
    return new UserInfo(id, "email@test.com", nickname, null, null, null, null);
  }

  // ──────────────────────────────────────────────────────────────────────────
  // findById
  // ──────────────────────────────────────────────────────────────────────────

  @Test
  @DisplayName("findById - 유저가 존재하면 UserSummary 반환")
  void findById_UserExists_ReturnsSummary() {
    // given
    given(loadUserInfoUseCase.loadUserById(1L)).willReturn(Optional.of(userInfo(1L, "nick-A")));

    // when
    Optional<UserSummary> result = sut.findById(1L);

    // then
    assertThat(result).isPresent();
    assertThat(result.get().userId()).isEqualTo(1L);
    assertThat(result.get().nickname()).isEqualTo("nick-A");
  }

  @Test
  @DisplayName("findById - 유저가 없으면 Optional.empty() 반환")
  void findById_UserNotFound_ReturnsEmpty() {
    // given
    given(loadUserInfoUseCase.loadUserById(99L)).willReturn(Optional.empty());

    // when
    Optional<UserSummary> result = sut.findById(99L);

    // then
    assertThat(result).isEmpty();
  }

  // ──────────────────────────────────────────────────────────────────────────
  // findByIds (batch)
  // ──────────────────────────────────────────────────────────────────────────

  @Test
  @DisplayName("findByIds - 배치 API를 단 한 번만 호출한다 (N+1 없음)")
  void findByIds_CallsBatchApiOnce() {
    // given
    List<UserInfo> users = List.of(userInfo(1L, "nick-A"), userInfo(2L, "nick-B"));
    given(loadUserInfoUseCase.loadUsersByIds(anyCollection()))
        .willReturn(users);

    // when
    sut.findByIds(List.of(1L, 2L));

    // then — loadUsersByIds must be called exactly once regardless of list size
    verify(loadUserInfoUseCase, times(1))
        .loadUsersByIds(anyCollection());
  }

  @Test
  @DisplayName("findByIds - 중복 ID가 있어도 같은 유저를 두 번 조회하지 않는다")
  void findByIds_DuplicateIds_DeduplicatedBeforeQuery() {
    // given — same trainerId appears 3 times (typical list scenario)
    List<UserInfo> users = List.of(userInfo(2L, "trainer-nick"));
    given(loadUserInfoUseCase.loadUsersByIds(anyCollection()))
        .willReturn(users);

    // when
    Map<Long, UserSummary> result = sut.findByIds(List.of(2L, 2L, 2L));

    // then — result has one entry; batch call received deduplicated collection
    assertThat(result).containsOnlyKeys(2L);
    assertThat(result.get(2L).nickname()).isEqualTo("trainer-nick");

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Collection<Long>> captor = ArgumentCaptor.forClass(Collection.class);
    verify(loadUserInfoUseCase).loadUsersByIds(captor.capture());
    assertThat(captor.getValue()).hasSize(1);
  }

  @Test
  @DisplayName("findByIds - 결과에 모든 유저 정보가 포함된다")
  void findByIds_MultipleUsers_AllMapped() {
    // given
    List<UserInfo> users = List.of(userInfo(1L, "nick-A"), userInfo(2L, "nick-B"));
    given(loadUserInfoUseCase.loadUsersByIds(anyCollection()))
        .willReturn(users);

    // when
    Map<Long, UserSummary> result = sut.findByIds(List.of(1L, 2L));

    // then
    assertThat(result).hasSize(2);
    assertThat(result.get(1L).nickname()).isEqualTo("nick-A");
    assertThat(result.get(2L).nickname()).isEqualTo("nick-B");
  }

  @Test
  @DisplayName("findByIds - 빈 목록이면 빈 맵 반환")
  void findByIds_EmptyInput_ReturnsEmptyMap() {
    // given
    given(loadUserInfoUseCase.loadUsersByIds(anyCollection()))
        .willReturn(List.of());

    // when
    Map<Long, UserSummary> result = sut.findByIds(List.of());

    // then
    assertThat(result).isEmpty();
  }
}
