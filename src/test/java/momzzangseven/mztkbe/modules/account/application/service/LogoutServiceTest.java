package momzzangseven.mztkbe.modules.account.application.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

import java.time.LocalDateTime;
import java.util.Optional;
import momzzangseven.mztkbe.global.error.token.RefreshTokenNotFoundException;
import momzzangseven.mztkbe.modules.account.application.delegation.RefreshTokenValidator;
import momzzangseven.mztkbe.modules.account.application.dto.LogoutCommand;
import momzzangseven.mztkbe.modules.account.application.port.out.LoadRefreshTokenPort;
import momzzangseven.mztkbe.modules.account.application.port.out.SaveRefreshTokenPort;
import momzzangseven.mztkbe.modules.account.domain.model.RefreshToken;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("LogoutService 단위 테스트")
class LogoutServiceTest {

  @Mock private LoadRefreshTokenPort loadRefreshTokenPort;
  @Mock private SaveRefreshTokenPort saveRefreshTokenPort;
  @Mock private RefreshTokenValidator refreshTokenValidator;

  @InjectMocks private LogoutService logoutService;

  private static final String VALID_TOKEN_VALUE = "eyJhbGciOiJIUzI1NiJ9.validTokenForLogout1234";

  private RefreshToken createActiveToken() {
    return RefreshToken.create(
        1L, VALID_TOKEN_VALUE, LocalDateTime.now().plusDays(1), LocalDateTime.now());
  }

  // ============================================
  // 성공 케이스
  // ============================================

  @Nested
  @DisplayName("execute() - 로그아웃 성공")
  class LogoutSuccessTest {

    @Test
    @DisplayName("유효한 토큰으로 로그아웃 시 토큰 revoke 후 저장")
    void execute_ValidToken_RevokesAndSaves() {
      LogoutCommand command = LogoutCommand.of(VALID_TOKEN_VALUE);
      RefreshToken token = createActiveToken();

      willDoNothing().given(refreshTokenValidator).validateJwtFormat(VALID_TOKEN_VALUE);
      given(loadRefreshTokenPort.findByTokenValueWithLock(VALID_TOKEN_VALUE))
          .willReturn(Optional.of(token));
      given(saveRefreshTokenPort.save(any(RefreshToken.class))).willReturn(token);

      logoutService.execute(command);

      verify(refreshTokenValidator, times(1)).validateJwtFormat(VALID_TOKEN_VALUE);
      verify(loadRefreshTokenPort, times(1)).findByTokenValueWithLock(VALID_TOKEN_VALUE);
      verify(saveRefreshTokenPort, times(1)).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("DB에 토큰이 없으면 저장 없이 정상 종료")
    void execute_TokenNotInDb_NoSave() {
      LogoutCommand command = LogoutCommand.of(VALID_TOKEN_VALUE);

      willDoNothing().given(refreshTokenValidator).validateJwtFormat(VALID_TOKEN_VALUE);
      given(loadRefreshTokenPort.findByTokenValueWithLock(VALID_TOKEN_VALUE))
          .willReturn(Optional.empty());

      logoutService.execute(command);

      verify(loadRefreshTokenPort, times(1)).findByTokenValueWithLock(VALID_TOKEN_VALUE);
      verifyNoInteractions(saveRefreshTokenPort);
    }
  }

  // ============================================
  // 관대한 처리 (Graceful handling)
  // ============================================

  @Nested
  @DisplayName("execute() - JWT 형식 오류 시 관대한 처리")
  class GracefulHandlingTest {

    @Test
    @DisplayName("JWT 형식 오류 토큰으로 로그아웃 시 예외 없이 조용히 종료")
    void execute_InvalidJwtFormat_SilentReturn() {
      LogoutCommand command = LogoutCommand.of("invalid-token-format");

      willThrow(new RefreshTokenNotFoundException("Invalid JWT format"))
          .given(refreshTokenValidator)
          .validateJwtFormat("invalid-token-format");

      // 예외 없이 정상 종료해야 함 (로그아웃은 관대하게 처리)
      org.assertj.core.api.Assertions.assertThatCode(() -> logoutService.execute(command))
          .doesNotThrowAnyException();

      verifyNoInteractions(loadRefreshTokenPort, saveRefreshTokenPort);
    }

    @Test
    @DisplayName("이미 revoke된 토큰으로 로그아웃 시 revoke 재호출 (멱등성)")
    void execute_AlreadyRevokedToken_NoException() {
      LogoutCommand command = LogoutCommand.of(VALID_TOKEN_VALUE);
      RefreshToken alreadyRevokedToken = createActiveToken();
      alreadyRevokedToken.revoke(); // 이미 revoke

      willDoNothing().given(refreshTokenValidator).validateJwtFormat(VALID_TOKEN_VALUE);
      given(loadRefreshTokenPort.findByTokenValueWithLock(VALID_TOKEN_VALUE))
          .willReturn(Optional.of(alreadyRevokedToken));
      given(saveRefreshTokenPort.save(any(RefreshToken.class))).willReturn(alreadyRevokedToken);

      org.assertj.core.api.Assertions.assertThatCode(() -> logoutService.execute(command))
          .doesNotThrowAnyException();

      verify(saveRefreshTokenPort, times(1)).save(any(RefreshToken.class));
    }
  }
}
