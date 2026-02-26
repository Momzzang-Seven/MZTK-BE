package momzzangseven.mztkbe.modules.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

import momzzangseven.mztkbe.modules.auth.application.dto.*;
import momzzangseven.mztkbe.modules.auth.application.strategy.AuthenticationStrategy;
import momzzangseven.mztkbe.modules.auth.application.strategy.AuthenticationStrategyFactory;
import momzzangseven.mztkbe.modules.auth.domain.model.AuthProvider;
import momzzangseven.mztkbe.modules.user.domain.model.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LoginServiceTest {

  @InjectMocks private LoginService loginService;

  @Mock private AuthenticationStrategyFactory strategyFactory;

  @Mock private AuthTokenIssuer tokenIssuer;

  @Mock private AuthenticationStrategy strategy; // 전략 객체 Mock

  @Test
  @DisplayName("로그인 성공: 전략을 통해 인증하고 토큰을 발급한다")
  void execute_success() {
    // --- Given (준비) ---
    AuthProvider provider = AuthProvider.KAKAO;

    // 1. 커맨드 객체 Mocking (실제 DTO 필드를 몰라도 됨)
    LoginCommand command = mock(LoginCommand.class);
    given(command.provider()).willReturn(provider);

    // 2. 팩토리가 'mockStrategy'를 반환하도록 설정
    given(strategyFactory.getStrategy(provider)).willReturn(strategy);

    // 3. 전략 실행 결과(AuthenticatedUser) 가짜 생성
    User mockUser = mock(User.class);
    AuthenticatedUser authUser = new AuthenticatedUser(mockUser, true);
    // ※ 주의: AuthenticatedUser 생성자가 다르다면 이 부분만 수정하세요!

    given(strategy.authenticate(any(AuthenticationContext.class))).willReturn(authUser);

    // 4. 토큰 발급 결과 가짜 생성
    LoginResult expectedResult = mock(LoginResult.class);
    given(tokenIssuer.issue(mockUser, true)).willReturn(expectedResult);

    // --- When (실행) ---
    LoginResult result = loginService.execute(command);

    // --- Then (검증) ---
    assertThat(result).isEqualTo(expectedResult);

    // 중요 로직들이 호출되었는지 확인
    verify(command).validate(); // 유효성 검사 호출 확인
    verify(strategyFactory).getStrategy(provider); // 팩토리 호출 확인
    verify(strategy).authenticate(any(AuthenticationContext.class)); // 인증 전략 실행 확인
    verify(tokenIssuer).issue(mockUser, true); // 토큰 발급 확인
  }
}
