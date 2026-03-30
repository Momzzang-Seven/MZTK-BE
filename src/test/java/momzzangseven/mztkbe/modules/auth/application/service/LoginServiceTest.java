package momzzangseven.mztkbe.modules.auth.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.*;

import java.util.Optional;
import momzzangseven.mztkbe.global.error.UnsupportedProviderException;
import momzzangseven.mztkbe.modules.auth.application.dto.AuthenticatedUser;
import momzzangseven.mztkbe.modules.auth.application.dto.IssuedTokens;
import momzzangseven.mztkbe.modules.auth.application.dto.LoginCommand;
import momzzangseven.mztkbe.modules.auth.application.dto.LoginResult;
import momzzangseven.mztkbe.modules.auth.application.port.out.LoadUserWalletPort;
import momzzangseven.mztkbe.modules.auth.application.strategy.AuthenticationStrategy;
import momzzangseven.mztkbe.modules.auth.application.strategy.AuthenticationStrategyFactory;
import momzzangseven.mztkbe.modules.auth.domain.model.AuthProvider;
import momzzangseven.mztkbe.modules.user.domain.model.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("LoginService 단위 테스트")
class LoginServiceTest {

  @Mock private AuthenticationStrategyFactory strategyFactory;
  @Mock private AuthTokenIssuer tokenIssuer;
  @Mock private AuthenticationStrategy mockStrategy;
  @Mock private LoadUserWalletPort loadUserWalletPort;

  @InjectMocks private LoginService loginService;

  private static final IssuedTokens STUB_TOKENS =
      new IssuedTokens("access-token", "refresh-token", "Bearer", 900L, 604800L);

  private User createFakeUser() {
    return User.builder()
        .id(1L)
        .email("user@example.com")
        .nickname("testuser")
        .authProvider(AuthProvider.LOCAL)
        .build();
  }

  // ============================================
  // 성공 케이스
  // ============================================

  @Nested
  @DisplayName("execute() - 로그인 성공")
  class LoginSuccessTest {

    @Test
    @DisplayName("LOCAL 로그인 성공 - 등록된 지갑 있음")
    void execute_LocalLogin_WithWallet_Success() {
      User user = createFakeUser();
      LoginCommand command =
          new LoginCommand(AuthProvider.LOCAL, "user@example.com", "password123", null, null);

      given(strategyFactory.getStrategy(AuthProvider.LOCAL)).willReturn(mockStrategy);
      given(mockStrategy.authenticate(any())).willReturn(AuthenticatedUser.existing(user));
      given(tokenIssuer.issueTokens(1L, "user@example.com", null)).willReturn(STUB_TOKENS);
      given(loadUserWalletPort.findActiveWalletAddress(1L)).willReturn(Optional.of("0xabc"));

      LoginResult result = loginService.execute(command);

      assertThat(result).isNotNull();
      assertThat(result.accessToken()).isEqualTo("access-token");
      assertThat(result.isNewUser()).isFalse();
      assertThat(result.walletAddress()).isEqualTo("0xabc");
      verify(strategyFactory, times(1)).getStrategy(AuthProvider.LOCAL);
      verify(mockStrategy, times(1)).authenticate(any());
      verify(tokenIssuer, times(1)).issueTokens(1L, "user@example.com", null);
      verify(loadUserWalletPort, times(1)).findActiveWalletAddress(1L);
    }

    @Test
    @DisplayName("LOCAL 로그인 성공 - 지갑 없으면 walletAddress null")
    void execute_LocalLogin_NoWallet_WalletAddressNull() {
      User user = createFakeUser();
      LoginCommand command =
          new LoginCommand(AuthProvider.LOCAL, "user@example.com", "password123", null, null);

      given(strategyFactory.getStrategy(AuthProvider.LOCAL)).willReturn(mockStrategy);
      given(mockStrategy.authenticate(any())).willReturn(AuthenticatedUser.existing(user));
      given(tokenIssuer.issueTokens(1L, "user@example.com", null)).willReturn(STUB_TOKENS);
      given(loadUserWalletPort.findActiveWalletAddress(1L)).willReturn(Optional.empty());

      LoginResult result = loginService.execute(command);

      assertThat(result.walletAddress()).isNull();
    }

    @Test
    @DisplayName("소셜 로그인 성공 - 신규 유저")
    void execute_SocialLogin_NewUser_Success() {
      User newUser = createFakeUser();
      LoginCommand command =
          new LoginCommand(AuthProvider.KAKAO, null, null, "kakao-auth-code", null);

      given(strategyFactory.getStrategy(AuthProvider.KAKAO)).willReturn(mockStrategy);
      given(mockStrategy.authenticate(any())).willReturn(AuthenticatedUser.newUser(newUser));
      given(tokenIssuer.issueTokens(eq(1L), any(), any())).willReturn(STUB_TOKENS);
      given(loadUserWalletPort.findActiveWalletAddress(1L)).willReturn(Optional.empty());

      LoginResult result = loginService.execute(command);

      assertThat(result.isNewUser()).isTrue();
    }
  }

  // ============================================
  // 실패 케이스
  // ============================================

  @Nested
  @DisplayName("execute() - 실패 케이스")
  class LoginFailureTest {

    @Test
    @DisplayName("provider가 null이면 IllegalArgumentException 발생")
    void execute_NullProvider_ThrowsException() {
      LoginCommand command = new LoginCommand(null, "email@test.com", "pw", null, null);

      assertThatThrownBy(() -> loginService.execute(command))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Provider is required");

      verifyNoInteractions(strategyFactory, tokenIssuer, loadUserWalletPort);
    }

    @Test
    @DisplayName("지원하지 않는 provider이면 UnsupportedProviderException 발생")
    void execute_UnsupportedProvider_ThrowsException() {
      LoginCommand command = new LoginCommand(AuthProvider.KAKAO, null, null, "auth-code", null);

      given(strategyFactory.getStrategy(AuthProvider.KAKAO))
          .willThrow(new UnsupportedProviderException(AuthProvider.KAKAO));

      assertThatThrownBy(() -> loginService.execute(command))
          .isInstanceOf(UnsupportedProviderException.class);

      verifyNoInteractions(tokenIssuer, loadUserWalletPort);
    }

    @Test
    @DisplayName("인증 전략이 예외를 던지면 그대로 전파됨")
    void execute_StrategyThrowsException_PropagatesException() {
      LoginCommand command =
          new LoginCommand(AuthProvider.LOCAL, "email@test.com", "wrong-pw", null, null);

      given(strategyFactory.getStrategy(AuthProvider.LOCAL)).willReturn(mockStrategy);
      given(mockStrategy.authenticate(any()))
          .willThrow(new RuntimeException("Authentication failed"));

      assertThatThrownBy(() -> loginService.execute(command))
          .isInstanceOf(RuntimeException.class)
          .hasMessageContaining("Authentication failed");

      verifyNoInteractions(tokenIssuer, loadUserWalletPort);
    }
  }
}
