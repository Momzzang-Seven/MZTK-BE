package momzzangseven.mztkbe.modules.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.time.Instant;
import java.util.Optional;
import momzzangseven.mztkbe.modules.user.application.dto.UserInfo;
import momzzangseven.mztkbe.modules.user.application.port.out.LoadUserPort;
import momzzangseven.mztkbe.modules.user.domain.model.User;
import momzzangseven.mztkbe.modules.user.domain.model.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("LoadUserInfoService 단위 테스트")
class LoadUserInfoServiceTest {

  @Mock private LoadUserPort loadUserPort;

  private LoadUserInfoService service;

  private User testUser;

  @BeforeEach
  void setUp() {
    service = new LoadUserInfoService(loadUserPort);

    Instant now = Instant.now();
    testUser =
        User.builder()
            .id(1L)
            .email("test@example.com")
            .nickname("tester")
            .profileImageUrl("https://img.example.com/1.png")
            .role(UserRole.USER)
            .createdAt(now)
            .updatedAt(now)
            .build();
  }

  @Nested
  @DisplayName("loadUserById")
  class LoadUserById {

    @Test
    @DisplayName("[M-38] 존재하는 userId → UserInfo 반환")
    void loadUserById_existingUser_returnsUserInfo() {
      // given
      given(loadUserPort.loadUserById(1L)).willReturn(Optional.of(testUser));

      // when
      Optional<UserInfo> result = service.loadUserById(1L);

      // then
      assertThat(result).isPresent();
      UserInfo info = result.get();
      assertThat(info.id()).isEqualTo(1L);
      assertThat(info.email()).isEqualTo("test@example.com");
      assertThat(info.nickname()).isEqualTo("tester");
      assertThat(info.role()).isEqualTo(UserRole.USER);
    }

    @Test
    @DisplayName("[M-39] 미존재 userId → empty 반환")
    void loadUserById_nonExistingUser_returnsEmpty() {
      // given
      given(loadUserPort.loadUserById(999L)).willReturn(Optional.empty());

      // when
      Optional<UserInfo> result = service.loadUserById(999L);

      // then
      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("loadUserByEmail")
  class LoadUserByEmail {

    @Test
    @DisplayName("[M-40] 존재하는 이메일 → UserInfo 반환")
    void loadUserByEmail_existingUser_returnsUserInfo() {
      // given
      given(loadUserPort.loadUserByEmail("test@example.com")).willReturn(Optional.of(testUser));

      // when
      Optional<UserInfo> result = service.loadUserByEmail("test@example.com");

      // then
      assertThat(result).isPresent();
      assertThat(result.get().email()).isEqualTo("test@example.com");
    }

    @Test
    @DisplayName("[M-41] 미존재 이메일 → empty 반환")
    void loadUserByEmail_nonExistingUser_returnsEmpty() {
      // given
      given(loadUserPort.loadUserByEmail("unknown@example.com")).willReturn(Optional.empty());

      // when
      Optional<UserInfo> result = service.loadUserByEmail("unknown@example.com");

      // then
      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("existsByEmail")
  class ExistsByEmail {

    @Test
    @DisplayName("[M-42] 존재하는 이메일 → true")
    void existsByEmail_existingEmail_returnsTrue() {
      // given
      given(loadUserPort.existsByEmail("test@example.com")).willReturn(true);

      // when & then
      assertThat(service.existsByEmail("test@example.com")).isTrue();
    }

    @Test
    @DisplayName("[M-43] 미존재 이메일 → false")
    void existsByEmail_nonExistingEmail_returnsFalse() {
      // given
      given(loadUserPort.existsByEmail("unknown@example.com")).willReturn(false);

      // when & then
      assertThat(service.existsByEmail("unknown@example.com")).isFalse();
    }
  }
}
