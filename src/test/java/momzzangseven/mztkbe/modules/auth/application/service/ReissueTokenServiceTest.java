package momzzangseven.mztkbe.modules.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.LocalDateTime;
import java.util.Optional;
import momzzangseven.mztkbe.global.error.UserNotFoundException;
import momzzangseven.mztkbe.global.error.token.RefreshTokenNotFoundException;
import momzzangseven.mztkbe.global.error.user.UserWithdrawnException;
import momzzangseven.mztkbe.global.security.JwtTokenProvider;
import momzzangseven.mztkbe.modules.auth.application.delegation.RefreshTokenManager;
import momzzangseven.mztkbe.modules.auth.application.delegation.RefreshTokenValidator;
import momzzangseven.mztkbe.modules.auth.application.dto.ReissueTokenCommand;
import momzzangseven.mztkbe.modules.auth.application.dto.ReissueTokenResult;
import momzzangseven.mztkbe.modules.auth.domain.model.AuthProvider;
import momzzangseven.mztkbe.modules.auth.domain.model.RefreshToken;
import momzzangseven.mztkbe.modules.user.application.port.out.LoadUserPort;
import momzzangseven.mztkbe.modules.user.domain.model.User;
import momzzangseven.mztkbe.modules.user.domain.model.UserRole;
import momzzangseven.mztkbe.modules.user.domain.model.UserStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReissueTokenService unit test")
class ReissueTokenServiceTest {

  @Mock private RefreshTokenValidator validator;
  @Mock private RefreshTokenManager refreshTokenManager;
  @Mock private JwtTokenProvider jwtTokenProvider;
  @Mock private LoadUserPort loadUserPort;

  @InjectMocks private ReissueTokenService reissueTokenService;

  @Test
  @DisplayName("execute() reissues token when request is valid")
  void execute_validCommand_reissuesToken() {
    String incomingRefreshToken = "refresh-token-12345";
    ReissueTokenCommand command = new ReissueTokenCommand(incomingRefreshToken);
    RefreshToken dbToken =
        RefreshToken.builder()
            .id(10L)
            .userId(1L)
            .tokenValue(incomingRefreshToken)
            .expiresAt(LocalDateTime.now().plusDays(1))
            .createdAt(LocalDateTime.now())
            .build();

    given(jwtTokenProvider.getUserIdFromToken(incomingRefreshToken)).willReturn(1L);
    given(loadUserPort.loadUserById(1L)).willReturn(Optional.of(activeUser(1L)));
    given(validator.inspectSecurityFlaw(incomingRefreshToken, 1L)).willReturn(dbToken);
    given(refreshTokenManager.rotateTokens(1L, dbToken))
        .willReturn(new RefreshTokenManager.TokenPair("new-access", "new-refresh"));
    given(jwtTokenProvider.getAccessTokenExpiresIn()).willReturn(900L);
    given(jwtTokenProvider.getRefreshTokenExpiresIn()).willReturn(3600L);

    ReissueTokenResult result = reissueTokenService.execute(command);

    assertThat(result.accessToken()).isEqualTo("new-access");
    assertThat(result.refreshToken()).isEqualTo("new-refresh");
    assertThat(result.grantType()).isEqualTo("Bearer");
    assertThat(result.accessTokenExpiresIn()).isEqualTo(900L);
    assertThat(result.refreshTokenExpiresIn()).isEqualTo(3600L);

    verify(validator).validateJwtFormat(incomingRefreshToken);
    verify(validator).inspectSecurityFlaw(incomingRefreshToken, 1L);
    verify(refreshTokenManager).rotateTokens(1L, dbToken);
  }

  @Test
  @DisplayName("execute() with blank refresh token throws RefreshTokenNotFoundException")
  void execute_blankRefreshToken_throwsRefreshTokenNotFoundException() {
    ReissueTokenCommand command = new ReissueTokenCommand(" ");

    assertThatThrownBy(() -> reissueTokenService.execute(command))
        .isInstanceOf(RefreshTokenNotFoundException.class)
        .hasMessage("Refresh token not found: Refresh token is required");

    verifyNoInteractions(validator, refreshTokenManager, jwtTokenProvider, loadUserPort);
  }

  @Test
  @DisplayName("execute() rejects withdrawn user")
  void execute_withdrawnUser_throwsUserWithdrawnException() {
    String incomingRefreshToken = "refresh-token-12345";
    ReissueTokenCommand command = new ReissueTokenCommand(incomingRefreshToken);

    given(jwtTokenProvider.getUserIdFromToken(incomingRefreshToken)).willReturn(7L);
    given(loadUserPort.loadUserById(7L)).willReturn(Optional.empty());
    given(loadUserPort.loadDeletedUserById(7L)).willReturn(Optional.of(deletedUser(7L)));

    assertThatThrownBy(() -> reissueTokenService.execute(command))
        .isInstanceOf(UserWithdrawnException.class);

    verify(validator).validateJwtFormat(incomingRefreshToken);
    verify(validator, never())
        .inspectSecurityFlaw(
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong());
    verify(refreshTokenManager, never())
        .rotateTokens(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any());
  }

  @Test
  @DisplayName("execute() throws UserNotFoundException when user does not exist")
  void execute_userMissing_throwsUserNotFoundException() {
    String incomingRefreshToken = "refresh-token-12345";
    ReissueTokenCommand command = new ReissueTokenCommand(incomingRefreshToken);

    given(jwtTokenProvider.getUserIdFromToken(incomingRefreshToken)).willReturn(8L);
    given(loadUserPort.loadUserById(8L)).willReturn(Optional.empty());
    given(loadUserPort.loadDeletedUserById(8L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> reissueTokenService.execute(command))
        .isInstanceOf(UserNotFoundException.class)
        .hasMessage("User not found with ID: 8");

    verify(validator).validateJwtFormat(incomingRefreshToken);
    verify(validator, never())
        .inspectSecurityFlaw(
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong());
    verify(refreshTokenManager, never())
        .rotateTokens(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any());
  }

  private User activeUser(Long id) {
    return User.builder()
        .id(id)
        .email("user@example.com")
        .authProvider(AuthProvider.LOCAL)
        .role(UserRole.USER)
        .status(UserStatus.ACTIVE)
        .build();
  }

  private User deletedUser(Long id) {
    return User.builder()
        .id(id)
        .email("deleted@example.com")
        .authProvider(AuthProvider.LOCAL)
        .role(UserRole.USER)
        .status(UserStatus.DELETED)
        .build();
  }
}
