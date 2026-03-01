package momzzangseven.mztkbe.modules.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.Optional;
import momzzangseven.mztkbe.global.error.InvalidCredentialsException;
import momzzangseven.mztkbe.modules.auth.application.dto.KakaoUserInfo;
import momzzangseven.mztkbe.modules.auth.application.dto.LoginResult;
import momzzangseven.mztkbe.modules.auth.application.dto.ReactivateCommand;
import momzzangseven.mztkbe.modules.auth.application.port.out.GoogleAuthPort;
import momzzangseven.mztkbe.modules.auth.application.port.out.KakaoAuthPort;
import momzzangseven.mztkbe.modules.auth.domain.model.AuthProvider;
import momzzangseven.mztkbe.modules.user.application.port.out.LoadUserPort;
import momzzangseven.mztkbe.modules.user.application.port.out.SaveUserPort;
import momzzangseven.mztkbe.modules.user.domain.model.User;
import momzzangseven.mztkbe.modules.user.domain.model.UserRole;
import momzzangseven.mztkbe.modules.user.domain.model.UserStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReactivateService unit test")
class ReactivateServiceTest {

  @Mock private LoadUserPort loadUserPort;
  @Mock private SaveUserPort saveUserPort;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private KakaoAuthPort kakaoAuthPort;
  @Mock private GoogleAuthPort googleAuthPort;
  @Mock private AuthTokenIssuer tokenIssuer;

  @InjectMocks private ReactivateService reactivateService;

  @Test
  @DisplayName("LOCAL deleted user is reactivated and token is issued")
  void execute_localDeletedUser_reactivatesAndIssuesToken() {
    ReactivateCommand command =
        new ReactivateCommand(AuthProvider.LOCAL, "user@example.com", "raw-password", null, null);
    User deletedUser =
        User.builder()
            .id(1L)
            .email("user@example.com")
            .password("encoded-password")
            .authProvider(AuthProvider.LOCAL)
            .status(UserStatus.DELETED)
            .role(UserRole.USER)
            .build();

    given(loadUserPort.loadDeletedUserByEmail("user@example.com"))
        .willReturn(Optional.of(deletedUser));
    given(passwordEncoder.matches("raw-password", "encoded-password")).willReturn(true);
    given(saveUserPort.saveUser(any(User.class)))
        .willAnswer(invocation -> invocation.getArgument(0));
    given(tokenIssuer.issue(any(User.class), org.mockito.ArgumentMatchers.eq(false)))
        .willReturn(LoginResult.of("access", "refresh", 10L, 20L, false, deletedUser.reactivate()));

    LoginResult result = reactivateService.execute(command);

    assertThat(result.accessToken()).isEqualTo("access");
    assertThat(result.refreshToken()).isEqualTo("refresh");
    assertThat(result.grantType()).isEqualTo("Bearer");
    assertThat(result.isNewUser()).isFalse();

    verify(loadUserPort).loadDeletedUserByEmail("user@example.com");
    verify(saveUserPort).saveUser(any(User.class));
    verify(tokenIssuer).issue(any(User.class), org.mockito.ArgumentMatchers.eq(false));
    verify(loadUserPort, never()).loadUserByEmail("user@example.com");
  }

  @Test
  @DisplayName("invalid LOCAL command is rejected before any collaborator call")
  void execute_invalidLocalCommand_rejectedBeforeCollaboratorCalls() {
    ReactivateCommand invalid = new ReactivateCommand(AuthProvider.LOCAL, " ", "pw", null, null);

    assertThatThrownBy(() -> reactivateService.execute(invalid))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Email is required for LOCAL reactivation");

    verifyNoInteractions(
        loadUserPort, saveUserPort, passwordEncoder, kakaoAuthPort, googleAuthPort, tokenIssuer);
  }

  @Test
  @DisplayName("social provider mismatch on deleted account throws InvalidCredentialsException")
  void execute_socialProviderMismatch_throwsInvalidCredentialsException() {
    ReactivateCommand command =
        new ReactivateCommand(AuthProvider.KAKAO, null, null, "auth-code", null);
    User wrongProviderDeletedUser =
        User.builder()
            .id(2L)
            .authProvider(AuthProvider.GOOGLE)
            .providerUserId("provider-1")
            .status(UserStatus.DELETED)
            .role(UserRole.USER)
            .build();

    given(kakaoAuthPort.getAccessToken("auth-code")).willReturn("kakao-access-token");
    given(kakaoAuthPort.getUserInfo("kakao-access-token"))
        .willReturn(
            KakaoUserInfo.builder()
                .providerUserId("provider-1")
                .email("social@example.com")
                .build());
    given(loadUserPort.findDeletedByProviderAndProviderUserId(AuthProvider.KAKAO, "provider-1"))
        .willReturn(Optional.of(wrongProviderDeletedUser));

    assertThatThrownBy(() -> reactivateService.execute(command))
        .isInstanceOf(InvalidCredentialsException.class)
        .hasMessage("Invalid email or password");

    verify(saveUserPort, never()).saveUser(any(User.class));
    verifyNoInteractions(tokenIssuer);
  }
}
