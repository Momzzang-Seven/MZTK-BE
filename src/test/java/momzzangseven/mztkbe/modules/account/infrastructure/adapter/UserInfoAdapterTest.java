package momzzangseven.mztkbe.modules.account.infrastructure.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;
import momzzangseven.mztkbe.modules.account.application.dto.AccountUserSnapshot;
import momzzangseven.mztkbe.modules.user.application.dto.UserInfo;
import momzzangseven.mztkbe.modules.user.application.port.in.LoadUserInfoUseCase;
import momzzangseven.mztkbe.modules.user.domain.model.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@DisplayName("UserInfoAdapter")
@ExtendWith(MockitoExtension.class)
class UserInfoAdapterTest {

  @Mock private LoadUserInfoUseCase loadUserInfoUseCase;

  @InjectMocks private UserInfoAdapter userInfoAdapter;

  private UserInfo sampleUserInfo() {
    return new UserInfo(
        1L,
        "test@example.com",
        "tester",
        "https://img.url/pic.jpg",
        UserRole.USER,
        LocalDateTime.now(),
        LocalDateTime.now());
  }

  @Nested
  @DisplayName("findById")
  class FindById {

    @Test
    @DisplayName("should convert UserInfo to AccountUserSnapshot")
    void shouldConvert() {
      when(loadUserInfoUseCase.loadUserById(1L)).thenReturn(Optional.of(sampleUserInfo()));

      Optional<AccountUserSnapshot> result = userInfoAdapter.findById(1L);

      assertThat(result).isPresent();
      AccountUserSnapshot snapshot = result.get();
      assertThat(snapshot.userId()).isEqualTo(1L);
      assertThat(snapshot.email()).isEqualTo("test@example.com");
      assertThat(snapshot.nickname()).isEqualTo("tester");
      assertThat(snapshot.role()).isEqualTo("USER");
    }

    @Test
    @DisplayName("should return empty when user not found")
    void shouldReturnEmpty() {
      when(loadUserInfoUseCase.loadUserById(999L)).thenReturn(Optional.empty());

      assertThat(userInfoAdapter.findById(999L)).isEmpty();
    }
  }

  @Nested
  @DisplayName("findByEmail")
  class FindByEmail {

    @Test
    @DisplayName("should convert UserInfo to AccountUserSnapshot")
    void shouldConvert() {
      when(loadUserInfoUseCase.loadUserByEmail("test@example.com"))
          .thenReturn(Optional.of(sampleUserInfo()));

      Optional<AccountUserSnapshot> result = userInfoAdapter.findByEmail("test@example.com");

      assertThat(result).isPresent();
      assertThat(result.get().email()).isEqualTo("test@example.com");
    }
  }

  @Nested
  @DisplayName("existsByEmail")
  class ExistsByEmail {

    @Test
    @DisplayName("should delegate to use case")
    void shouldDelegate() {
      when(loadUserInfoUseCase.existsByEmail("test@example.com")).thenReturn(true);

      assertThat(userInfoAdapter.existsByEmail("test@example.com")).isTrue();
    }
  }
}
