package momzzangseven.mztkbe.modules.auth.application.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.*;

import java.util.Optional;
import momzzangseven.mztkbe.global.error.token.RefreshTokenNotFoundException;
import momzzangseven.mztkbe.modules.auth.application.delegation.RefreshTokenValidator;
import momzzangseven.mztkbe.modules.auth.application.dto.LogoutCommand;
import momzzangseven.mztkbe.modules.auth.application.port.out.LoadRefreshTokenPort;
import momzzangseven.mztkbe.modules.auth.application.port.out.SaveRefreshTokenPort;
import momzzangseven.mztkbe.modules.auth.domain.model.RefreshToken;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LogoutServiceTest {

  @InjectMocks private LogoutService logoutService;

  @Mock private LoadRefreshTokenPort loadRefreshTokenPort;

  @Mock private SaveRefreshTokenPort saveRefreshTokenPort;

  @Mock private RefreshTokenValidator refreshTokenValidator;

  @Test
  @DisplayName("로그아웃 성공: 유효한 토큰이면 revoke()하고 저장한다")
  void execute_success() {
    // --- Given ---
    String validToken = "valid_refresh_token";

    // Command Mocking
    LogoutCommand command = mock(LogoutCommand.class);
    given(command.getRefreshToken()).willReturn(validToken);

    // RefreshToken Mocking
    RefreshToken mockToken = mock(RefreshToken.class);

    // DB에서 토큰을 찾았다고 가정
    given(loadRefreshTokenPort.findByTokenValueWithLock(validToken))
        .willReturn(Optional.of(mockToken));

    // --- When ---
    logoutService.execute(command);

    // --- Then ---
    verify(refreshTokenValidator).validateJwtFormat(validToken); // 포맷 검증 확인
    verify(mockToken).revoke(); // 토큰 만료 처리 확인
    verify(saveRefreshTokenPort).save(mockToken); // 저장 확인
  }

  @Test
  @DisplayName("로그아웃 실패: 토큰 형식이 이상하면(예외발생) 무시하고 종료한다")
  void execute_fail_invalid_format() {
    // --- Given ---
    String invalidToken = "invalid_token";
    LogoutCommand command = mock(LogoutCommand.class);
    given(command.getRefreshToken()).willReturn(invalidToken);

    // Validator가 예외를 던지도록 설정
    willThrow(new RefreshTokenNotFoundException("Invalid Token"))
        .given(refreshTokenValidator)
        .validateJwtFormat(invalidToken);

    // --- When ---
    logoutService.execute(command);

    // --- Then ---
    verify(loadRefreshTokenPort, never()).findByTokenValueWithLock(anyString());
    verify(saveRefreshTokenPort, never()).save(any());
  }

  @Test
  @DisplayName("로그아웃 실패: DB에 없는 토큰인 경우 아무 작업도 하지 않는다")
  void execute_fail_token_not_found() {
    // --- Given ---
    String notFoundToken = "not_found_token";
    LogoutCommand command = mock(LogoutCommand.class);
    given(command.getRefreshToken()).willReturn(notFoundToken);

    // DB 조회 결과가 Empty
    given(loadRefreshTokenPort.findByTokenValueWithLock(notFoundToken))
        .willReturn(Optional.empty());

    // --- When ---
    logoutService.execute(command);

    // --- Then ---
    verify(refreshTokenValidator).validateJwtFormat(notFoundToken);
    verify(saveRefreshTokenPort, never()).save(any()); // 저장 호출 X
  }
}
