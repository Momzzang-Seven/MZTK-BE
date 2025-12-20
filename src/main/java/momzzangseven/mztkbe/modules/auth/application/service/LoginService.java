package momzzangseven.mztkbe.modules.auth.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.security.JwtTokenProvider;
import momzzangseven.mztkbe.modules.auth.application.dto.AuthenticatedUser;
import momzzangseven.mztkbe.modules.auth.application.dto.AuthenticationContext;
import momzzangseven.mztkbe.modules.auth.application.dto.LoginCommand;
import momzzangseven.mztkbe.modules.auth.application.dto.LoginResult;
import momzzangseven.mztkbe.modules.auth.application.port.in.LoginUseCase;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.auth.application.strategy.AuthenticationStrategy;
import momzzangseven.mztkbe.modules.auth.application.strategy.AuthenticationStrategyFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class LoginService implements LoginUseCase {

    private final AuthenticationStrategyFactory strategyFactory;
    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public LoginResult execute(LoginCommand command) {
        log.info("Login request received for provider: {}", command.provider());

        // Step 1: Validate command
        command.validate();

        // Step 2: Get appropriate strategy
        AuthenticationStrategy strategy = strategyFactory.getStrategy(
                command.provider()
        );

        // Step 3: Convert LoginCommand to AuthenticationContext
        AuthenticationContext context = AuthenticationContext.from(command);

        // Step 4: Authenticate user via strategy
        AuthenticatedUser authenticatedUser = strategy.authenticate(context);

        // Step 5: Generate JWT tokens
        String accessToken = jwtTokenProvider.generateAccessToken(
                authenticatedUser.user().getId(),
                authenticatedUser.user().getEmail(),
                authenticatedUser.user().getRole()
        );
        String refreshToken = jwtTokenProvider.generateRefreshToken(
                authenticatedUser.user().getId()
        );

        log.info("Login successful for user: {}, isNewUser: {}",
                authenticatedUser.user().getId(),
                authenticatedUser.isNewUser()
        );

        // Step 6: Build result
        return LoginResult.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .grantType("Bearer")
                .expiresIn(1800)  // 30 minutes
                .isNewUser(authenticatedUser.isNewUser())
                .user(authenticatedUser.user())
                .build();
    }
}