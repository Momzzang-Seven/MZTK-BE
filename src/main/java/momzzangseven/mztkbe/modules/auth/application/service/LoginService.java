package momzzangseven.mztkbe.modules.auth.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.auth.application.dto.AuthenticatedUser;
import momzzangseven.mztkbe.modules.auth.application.dto.AuthenticationContext;
import momzzangseven.mztkbe.modules.auth.application.dto.LoginResult;
import momzzangseven.mztkbe.modules.auth.application.port.in.LoginUseCase;
import momzzangseven.mztkbe.modules.auth.application.strategy.AuthenticationStrategyFactory;
import momzzangseven.mztkbe.modules.user.application.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LoginService implements LoginUseCase {

  private final AuthenticationStrategyFactory strategyFactory;
  private final UserService userService;

  // private final JwtTokenProvider jwtTokenProvider; // 있으면 나중에 연결

  @Override
  @Transactional
  public LoginResult login(AuthenticationContext context) {
    var strategy = strategyFactory.getStrategy(context.getProvider());

    AuthenticatedUser au = strategy.authenticate(context);

    // ✅ 여기서 user 생성/중복정책/저장 처리 (user 모듈로 위임)
    UserService.SocialLoginOutcome outcome =
        userService.loginOrRegisterSocial(
            au.getProvider(),
            au.getProviderUserId(),
            au.getEmail(),
            au.getNickname(),
            au.getProfileImageUrl());

    // TODO: 토큰 붙이기
    String accessToken = null;
    String refreshToken = null;

    return LoginResult.of(outcome.user(), outcome.isNewUser(), accessToken, refreshToken);
  }
}
