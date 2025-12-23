package momzzangseven.mztkbe.modules.auth.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.security.JwtTokenProvider;
import momzzangseven.mztkbe.modules.auth.application.dto.AuthenticatedUser;
import momzzangseven.mztkbe.modules.auth.application.dto.AuthenticationContext;
import momzzangseven.mztkbe.modules.auth.application.dto.LoginCommand;
import momzzangseven.mztkbe.modules.auth.application.dto.LoginResult;
import momzzangseven.mztkbe.modules.auth.application.port.in.LoginUseCase;
import momzzangseven.mztkbe.modules.auth.application.strategy.AuthenticationStrategy;
import momzzangseven.mztkbe.modules.auth.application.strategy.AuthenticationStrategyFactory;
import momzzangseven.mztkbe.modules.user.application.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class LoginService implements LoginUseCase {

  private final AuthenticationStrategyFactory strategyFactory;
  private final UserService userService;
  private final JwtTokenProvider jwtTokenProvider;

  @Override
  public LoginResult execute(LoginCommand command) {
    log.info("Login request received for provider: {}", command.provider());

    // Validate command
    command.validate();

    // Strategy 선택
    AuthenticationStrategy strategy =
            strategyFactory.getStrategy(command.provider());

    //  Context 생성
    AuthenticationContext context =
            AuthenticationContext.from(command);

    //  인증 + 유저조회/가입까지 끝낸 최종 결과
    AuthenticatedUser authenticatedUser = strategy.authenticate(context);


    // JWT 발급
    String accessToken =
            jwtTokenProvider.generateAccessToken(
                    authenticatedUser.user().getId(),
                    authenticatedUser.user().getEmail(),
                    authenticatedUser.user().getRole()
            );

    String refreshToken =
            jwtTokenProvider.generateRefreshToken(
                    authenticatedUser.user().getId()
            );

    log.info(
            "Login successful for user: {}, isNewUser: {}",
            authenticatedUser.user().getId(),
            authenticatedUser.isNewUser()
    );

    // Response DTO 생성
    return LoginResult.builder()
            .accessToken(accessToken)
            .refreshToken(refreshToken)
            .grantType("Bearer")
            .expiresIn(1800)
            .isNewUser(authenticatedUser.isNewUser())
            .user(authenticatedUser.user())
            .build();
  }
}
