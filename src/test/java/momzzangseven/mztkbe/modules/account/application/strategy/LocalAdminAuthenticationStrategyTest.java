package momzzangseven.mztkbe.modules.account.application.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import java.util.Optional;
import momzzangseven.mztkbe.global.error.InvalidCredentialsException;
import momzzangseven.mztkbe.modules.account.application.dto.AccountUserSnapshot;
import momzzangseven.mztkbe.modules.account.application.dto.AuthenticatedUser;
import momzzangseven.mztkbe.modules.account.application.dto.AuthenticationContext;
import momzzangseven.mztkbe.modules.account.application.port.out.AdminLocalAuthPort;
import momzzangseven.mztkbe.modules.account.application.port.out.LoadAccountUserInfoPort;
import momzzangseven.mztkbe.modules.account.domain.vo.AuthProvider;
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

  @Mock private AdminLocalAuthPort adminLocalAuthPort;
  @Mock private LoadAccountUserInfoPort loadAccountUserInfoPort;

  @InjectMocks private LocalAdminAuthenticationStrategy strategy;

  private AuthenticationContext adminContext(String loginId, String password) {
    return new AuthenticationContext(
        AuthProvider.LOCAL_ADMIN, null, password, null, null, null, loginId);
  }

  private AccountUserSnapshot createSnapshot(Long userId) {
    return new AccountUserSnapshot(userId, "admin@test.com", "Admin", null, "ADMIN_SEED");
  }

  @Test
  @DisplayName("supports() returns LOCAL_ADMIN")
  void supports_ReturnsLocalAdmin() {
    assertThat(strategy.supports()).isEqualTo(AuthProvider.LOCAL_ADMIN);
  }

  @Nested
  @DisplayName("성공 케이스")
  class SuccessCases {

    @Test
    @DisplayName("유효한 자격 증명으로 인증하면 AuthenticatedUser를 반환한다")
    void authenticate_ValidCredentials_ReturnsAuthenticatedUser() {
      // given
      given(adminLocalAuthPort.authenticateAndGetUserId("admin001", "rawPassword")).willReturn(1L);
      given(loadAccountUserInfoPort.findById(1L)).willReturn(Optional.of(createSnapshot(1L)));

      // when
      AuthenticatedUser result = strategy.authenticate(adminContext("admin001", "rawPassword"));

      // then
      assertThat(result.isNewUser()).isFalse();
      assertThat(result.userSnapshot().userId()).isEqualTo(1L);
      assertThat(result.userSnapshot().email()).isEqualTo("admin@test.com");
      assertThat(result.userSnapshot().nickname()).isEqualTo("Admin");
      assertThat(result.userSnapshot().role()).isEqualTo("ADMIN_SEED");
    }
  }

  @Nested
  @DisplayName("실패 케이스")
  class FailureCases {

    @Test
    @DisplayName("admin 인증 실패 시 InvalidCredentialsException이 전파된다")
    void authenticate_AdminAuthFails_PropagatesException() {
      // given
      given(adminLocalAuthPort.authenticateAndGetUserId("unknown", "rawPassword"))
          .willThrow(new InvalidCredentialsException("Invalid admin credentials"));

      // when & then
      assertThatThrownBy(() -> strategy.authenticate(adminContext("unknown", "rawPassword")))
          .isInstanceOf(InvalidCredentialsException.class)
          .hasMessageContaining("Invalid admin credentials");
    }

    @Test
    @DisplayName("사용자 스냅샷 조회 실패 시 InvalidCredentialsException을 던진다")
    void authenticate_UserSnapshotNotFound_ThrowsInvalidCredentials() {
      // given
      given(adminLocalAuthPort.authenticateAndGetUserId("admin001", "rawPassword"))
          .willReturn(999L);
      given(loadAccountUserInfoPort.findById(999L)).willReturn(Optional.empty());

      // when & then
      assertThatThrownBy(() -> strategy.authenticate(adminContext("admin001", "rawPassword")))
          .isInstanceOf(InvalidCredentialsException.class)
          .hasMessageContaining("Admin user not found");
    }

    @Test
    @DisplayName("context 유효성 검증 실패 시 InvalidCredentialsException을 던진다")
    void authenticate_InvalidContext_ThrowsInvalidCredentials() {
      // given — loginId is null
      AuthenticationContext invalidContext =
          new AuthenticationContext(
              AuthProvider.LOCAL_ADMIN, null, "password", null, null, null, null);

      // when & then
      assertThatThrownBy(() -> strategy.authenticate(invalidContext))
          .isInstanceOf(InvalidCredentialsException.class)
          .hasMessageContaining("LoginId and password are required");
    }
  }
}
