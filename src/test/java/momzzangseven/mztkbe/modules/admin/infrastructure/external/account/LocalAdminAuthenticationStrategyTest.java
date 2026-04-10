package momzzangseven.mztkbe.modules.admin.infrastructure.external.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.Optional;
import momzzangseven.mztkbe.global.error.InvalidCredentialsException;
import momzzangseven.mztkbe.modules.account.application.dto.AuthenticatedUser;
import momzzangseven.mztkbe.modules.account.application.dto.AuthenticationContext;
import momzzangseven.mztkbe.modules.account.domain.vo.AuthProvider;
import momzzangseven.mztkbe.modules.admin.application.port.out.AdminPasswordEncoderPort;
import momzzangseven.mztkbe.modules.admin.application.port.out.LoadAdminAccountPort;
import momzzangseven.mztkbe.modules.admin.application.port.out.SaveAdminAccountPort;
import momzzangseven.mztkbe.modules.admin.domain.model.AdminAccount;
import momzzangseven.mztkbe.modules.user.application.port.out.LoadUserPort;
import momzzangseven.mztkbe.modules.user.domain.model.User;
import momzzangseven.mztkbe.modules.user.domain.model.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("LocalAdminAuthenticationStrategy 단위 테스트")
class LocalAdminAuthenticationStrategyTest {

  @Mock private LoadAdminAccountPort loadAdminAccountPort;
  @Mock private AdminPasswordEncoderPort adminPasswordEncoderPort;
  @Mock private SaveAdminAccountPort saveAdminAccountPort;
  @Mock private LoadUserPort loadUserPort;

  @InjectMocks private LocalAdminAuthenticationStrategy strategy;

  // ============================================
  // Test fixtures
  // ============================================

  private AdminAccount createAdminAccount(Long userId, String loginId, String passwordHash) {
    return AdminAccount.builder()
        .id(1L)
        .userId(userId)
        .loginId(loginId)
        .passwordHash(passwordHash)
        .createdBy(null)
        .passwordLastRotatedAt(Instant.now())
        .createdAt(Instant.now())
        .updatedAt(Instant.now())
        .build();
  }

  private User createAdminUser(Long id, String email, String nickname, UserRole role) {
    return User.builder()
        .id(id)
        .email(email)
        .nickname(nickname)
        .profileImageUrl(null)
        .role(role)
        .createdAt(Instant.now())
        .updatedAt(Instant.now())
        .build();
  }

  private AuthenticationContext adminContext(String loginId, String password) {
    return new AuthenticationContext(
        AuthProvider.LOCAL_ADMIN, null, password, null, null, null, loginId);
  }

  // ============================================
  // supports()
  // ============================================

  @Test
  @DisplayName("[M-120] supports() returns LOCAL_ADMIN")
  void supports_ReturnsLocalAdmin() {
    // when
    AuthProvider result = strategy.supports();

    // then
    assertThat(result).isEqualTo(AuthProvider.LOCAL_ADMIN);
  }

  // ============================================
  // authenticate() - success cases
  // ============================================

  @Nested
  @DisplayName("성공 케이스")
  class SuccessCases {

    @Test
    @DisplayName("[M-121] authenticate succeeds with valid loginId and password")
    void authenticate_ValidCredentials_ReturnsAuthenticatedUser() {
      // given
      AdminAccount account = createAdminAccount(1L, "admin001", "hashed");
      User user = createAdminUser(1L, "admin@test.com", "Admin", UserRole.ADMIN_SEED);

      given(loadAdminAccountPort.findActiveByLoginId("admin001")).willReturn(Optional.of(account));
      given(adminPasswordEncoderPort.matches("rawPassword", "hashed")).willReturn(true);
      given(saveAdminAccountPort.save(any(AdminAccount.class)))
          .willAnswer(inv -> inv.getArgument(0));
      given(loadUserPort.loadUserById(1L)).willReturn(Optional.of(user));

      // when
      AuthenticatedUser result = strategy.authenticate(adminContext("admin001", "rawPassword"));

      // then
      assertThat(result.isNewUser()).isFalse();
      assertThat(result.userSnapshot().userId()).isEqualTo(1L);
      assertThat(result.userSnapshot().email()).isEqualTo("admin@test.com");
      assertThat(result.userSnapshot().nickname()).isEqualTo("Admin");
      assertThat(result.userSnapshot().role()).isEqualTo("ADMIN_SEED");
      verify(saveAdminAccountPort, times(1)).save(any(AdminAccount.class));
    }

    @Test
    @DisplayName("[M-125] authenticate updates lastLogin on successful auth")
    void authenticate_Success_UpdatesLastLogin() {
      // given
      AdminAccount account = createAdminAccount(1L, "admin001", "hashed");
      User user = createAdminUser(1L, "admin@test.com", "Admin", UserRole.ADMIN_SEED);

      given(loadAdminAccountPort.findActiveByLoginId("admin001")).willReturn(Optional.of(account));
      given(adminPasswordEncoderPort.matches("rawPassword", "hashed")).willReturn(true);
      given(saveAdminAccountPort.save(any(AdminAccount.class)))
          .willAnswer(inv -> inv.getArgument(0));
      given(loadUserPort.loadUserById(1L)).willReturn(Optional.of(user));

      // when
      strategy.authenticate(adminContext("admin001", "rawPassword"));

      // then
      verify(saveAdminAccountPort, times(1)).save(any(AdminAccount.class));
    }
  }

  // ============================================
  // authenticate() - failure cases
  // ============================================

  @Nested
  @DisplayName("실패 케이스")
  class FailureCases {

    @Test
    @DisplayName("[M-122] authenticate throws when admin account not found")
    void authenticate_AccountNotFound_ThrowsInvalidCredentials() {
      // given
      given(loadAdminAccountPort.findActiveByLoginId("unknown")).willReturn(Optional.empty());

      // when & then
      assertThatThrownBy(() -> strategy.authenticate(adminContext("unknown", "rawPassword")))
          .isInstanceOf(InvalidCredentialsException.class)
          .hasMessageContaining("Invalid admin credentials");

      verify(adminPasswordEncoderPort, never()).matches(any(), any());
      verify(saveAdminAccountPort, never()).save(any());
    }

    @Test
    @DisplayName("[M-123] authenticate throws when password does not match")
    void authenticate_WrongPassword_ThrowsInvalidCredentials() {
      // given
      AdminAccount account = createAdminAccount(1L, "admin001", "hashed");

      given(loadAdminAccountPort.findActiveByLoginId("admin001")).willReturn(Optional.of(account));
      given(adminPasswordEncoderPort.matches("wrongPassword", "hashed")).willReturn(false);

      // when & then
      assertThatThrownBy(() -> strategy.authenticate(adminContext("admin001", "wrongPassword")))
          .isInstanceOf(InvalidCredentialsException.class)
          .hasMessageContaining("Invalid admin credentials");

      verify(saveAdminAccountPort, never()).save(any());
    }

    @Test
    @DisplayName("[M-124] authenticate throws when linked user not found")
    void authenticate_UserNotFound_ThrowsInvalidCredentials() {
      // given
      AdminAccount account = createAdminAccount(999L, "admin001", "hashed");

      given(loadAdminAccountPort.findActiveByLoginId("admin001")).willReturn(Optional.of(account));
      given(adminPasswordEncoderPort.matches("rawPassword", "hashed")).willReturn(true);
      given(saveAdminAccountPort.save(any(AdminAccount.class)))
          .willAnswer(inv -> inv.getArgument(0));
      given(loadUserPort.loadUserById(999L)).willReturn(Optional.empty());

      // when & then
      assertThatThrownBy(() -> strategy.authenticate(adminContext("admin001", "rawPassword")))
          .isInstanceOf(InvalidCredentialsException.class)
          .hasMessageContaining("Admin user not found");
    }
  }
}
