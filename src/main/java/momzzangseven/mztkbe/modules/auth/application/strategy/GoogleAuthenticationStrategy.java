package momzzangseven.mztkbe.modules.auth.application.strategy;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.auth.application.dto.AuthenticatedUser;
import momzzangseven.mztkbe.modules.auth.application.dto.AuthenticationContext;
import momzzangseven.mztkbe.modules.auth.application.dto.GoogleUserInfo;
import momzzangseven.mztkbe.modules.auth.application.port.out.GoogleAuthPort;
import momzzangseven.mztkbe.modules.auth.domain.model.AuthProvider;
import momzzangseven.mztkbe.modules.user.application.port.in.SocialLoginOutcome;
import momzzangseven.mztkbe.modules.user.application.port.in.SocialLoginUseCase;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GoogleAuthenticationStrategy implements AuthenticationStrategy {

    private final GoogleAuthPort googleAuthPort;
    private final SocialLoginUseCase socialLoginUseCase;

    @Override
    public AuthProvider supports() {
        return AuthProvider.GOOGLE;
    }

    @Override
    public AuthenticatedUser authenticate(AuthenticationContext context) {

        String accessToken = googleAuthPort.getAccessToken(context.authorizationCode());
        GoogleUserInfo info = googleAuthPort.getUserInfo(accessToken);

        if (info.getEmail() == null || info.getEmail().isBlank()) {
            throw new IllegalArgumentException("Google email is required but not provided.");
        }

        SocialLoginOutcome outcome =
                socialLoginUseCase.loginOrRegisterSocial(
                        AuthProvider.GOOGLE.name(),
                        info.getProviderUserId(),   // sub
                        info.getEmail(),
                        info.getNickname(),         // name
                        info.getProfileImageUrl()); // picture

        return outcome.isNewUser()
                ? AuthenticatedUser.newUser(outcome.user())
                : AuthenticatedUser.existing(outcome.user());
    }
}
