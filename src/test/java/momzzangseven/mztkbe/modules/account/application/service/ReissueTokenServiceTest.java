package momzzangseven.mztkbe.modules.account.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import momzzangseven.mztkbe.global.error.UserNotFoundException;
import momzzangseven.mztkbe.global.error.token.RefreshTokenNotFoundException;
import momzzangseven.mztkbe.global.error.user.UserBlockedException;
import momzzangseven.mztkbe.global.error.user.UserWithdrawnException;
import momzzangseven.mztkbe.global.security.JwtTokenProvider;
import momzzangseven.mztkbe.modules.account.application.delegation.RefreshTokenManager;
import momzzangseven.mztkbe.modules.account.application.delegation.RefreshTokenValidator;
import momzzangseven.mztkbe.modules.account.application.dto.ReissueTokenCommand;
import momzzangseven.mztkbe.modules.account.application.dto.ReissueTokenResult;
import momzzangseven.mztkbe.modules.account.application.port.out.CheckAdminRefreshSubjectPort;
import momzzangseven.mztkbe.modules.account.application.port.out.LoadUserAccountPort;
import momzzangseven.mztkbe.modules.account.domain.model.RefreshToken;
import momzzangseven.mztkbe.modules.account.domain.model.UserAccount;
import momzzangseven.mztkbe.modules.account.domain.vo.AccountStatus;
import momzzangseven.mztkbe.modules.account.domain.vo.AuthProvider;
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
  @Mock private LoadUserAccountPort loadUserAccountPort;
  @Mock private CheckAdminRefreshSubjectPort checkAdminRefreshSubjectPort;

  @InjectMocks private ReissueTokenService reissueTokenService;

  @Test
  @DisplayName("execute() reissues token when request is valid")
  void execute_validCommand_reissuesToken() {
    String incomingRefreshToken = "refresh-token-12345";
    ReissueTokenCommand command = new ReissueTokenCommand(incomingRefreshToken);
    Instant now = Instant.now();
    RefreshToken dbToken =
        RefreshToken.builder()
            .id(10L)
            .userId(1L)
            .tokenValue(incomingRefreshToken)
            .expiresAt(now.plus(Duration.ofDays(1)))
            .createdAt(now)
            .build();

    given(jwtTokenProvider.getUserIdFromToken(incomingRefreshToken)).willReturn(1L);
    given(loadUserAccountPort.findByUserId(1L)).willReturn(Optional.of(activeAccount(1L)));
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

    verifyNoInteractions(
        validator,
        refreshTokenManager,
        jwtTokenProvider,
        loadUserAccountPort,
        checkAdminRefreshSubjectPort);
  }

  @Test
  @DisplayName("execute() rejects withdrawn user")
  void execute_withdrawnUser_throwsUserWithdrawnException() {
    String incomingRefreshToken = "refresh-token-12345";
    ReissueTokenCommand command = new ReissueTokenCommand(incomingRefreshToken);

    given(jwtTokenProvider.getUserIdFromToken(incomingRefreshToken)).willReturn(7L);
    given(loadUserAccountPort.findByUserId(7L)).willReturn(Optional.of(deletedAccount(7L)));

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
  @DisplayName("execute() rejects blocked user")
  void execute_blockedUser_throwsUserBlockedException() {
    String incomingRefreshToken = "refresh-token-12345";
    ReissueTokenCommand command = new ReissueTokenCommand(incomingRefreshToken);

    given(jwtTokenProvider.getUserIdFromToken(incomingRefreshToken)).willReturn(7L);
    given(loadUserAccountPort.findByUserId(7L)).willReturn(Optional.of(blockedAccount(7L)));

    assertThatThrownBy(() -> reissueTokenService.execute(command))
        .isInstanceOf(UserBlockedException.class);

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
    given(loadUserAccountPort.findByUserId(8L)).willReturn(Optional.empty());
    given(checkAdminRefreshSubjectPort.isActiveAdmin(8L)).willReturn(false);

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

  @Test
  @DisplayName("execute() reissues token for active LOCAL_ADMIN subject without users_account row")
  void execute_activeAdminWithoutUserAccount_reissuesToken() {
    String incomingRefreshToken = "refresh-token-admin";
    ReissueTokenCommand command = new ReissueTokenCommand(incomingRefreshToken);
    Instant now = Instant.now();
    RefreshToken dbToken =
        RefreshToken.builder()
            .id(10L)
            .userId(99L)
            .tokenValue(incomingRefreshToken)
            .expiresAt(now.plus(Duration.ofDays(1)))
            .createdAt(now)
            .build();

    given(jwtTokenProvider.getUserIdFromToken(incomingRefreshToken)).willReturn(99L);
    given(loadUserAccountPort.findByUserId(99L)).willReturn(Optional.empty());
    given(checkAdminRefreshSubjectPort.isActiveAdmin(99L)).willReturn(true);
    given(validator.inspectSecurityFlaw(incomingRefreshToken, 99L)).willReturn(dbToken);
    given(refreshTokenManager.rotateTokens(99L, dbToken))
        .willReturn(new RefreshTokenManager.TokenPair("new-admin-access", "new-admin-refresh"));
    given(jwtTokenProvider.getAccessTokenExpiresIn()).willReturn(900L);
    given(jwtTokenProvider.getRefreshTokenExpiresIn()).willReturn(3600L);

    ReissueTokenResult result = reissueTokenService.execute(command);

    assertThat(result.accessToken()).isEqualTo("new-admin-access");
    assertThat(result.refreshToken()).isEqualTo("new-admin-refresh");
    verify(validator).inspectSecurityFlaw(incomingRefreshToken, 99L);
    verify(refreshTokenManager).rotateTokens(99L, dbToken);
  }

  private UserAccount activeAccount(Long userId) {
    return UserAccount.builder()
        .userId(userId)
        .provider(AuthProvider.LOCAL)
        .status(AccountStatus.ACTIVE)
        .build();
  }

  private UserAccount deletedAccount(Long userId) {
    return UserAccount.builder()
        .userId(userId)
        .provider(AuthProvider.LOCAL)
        .status(AccountStatus.DELETED)
        .build();
  }

  private UserAccount blockedAccount(Long userId) {
    return UserAccount.builder()
        .userId(userId)
        .provider(AuthProvider.LOCAL)
        .status(AccountStatus.BLOCKED)
        .build();
  }
}
