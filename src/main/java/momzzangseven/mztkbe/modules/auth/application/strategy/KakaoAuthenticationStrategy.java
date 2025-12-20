package momzzangseven.mztkbe.modules.auth.application.strategy;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.InvalidCredentialsException;
import momzzangseven.mztkbe.modules.auth.application.dto.AuthenticatedUser;
import momzzangseven.mztkbe.modules.auth.application.dto.AuthenticationContext;
import momzzangseven.mztkbe.modules.auth.application.dto.KakaoUserInfo;
import momzzangseven.mztkbe.modules.auth.application.port.out.KakaoAuthPort;
import momzzangseven.mztkbe.modules.auth.domain.model.AuthProvider;
import momzzangseven.mztkbe.modules.user.application.port.out.LoadUserPort;
import momzzangseven.mztkbe.modules.user.application.port.out.SaveUserPort;
import momzzangseven.mztkbe.modules.user.domain.model.User;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class KakaoAuthenticationStrategy implements AuthenticationStrategy{
    // outbound port of auth module
    private final KakaoAuthPort kakaoAuthPort;

    // outbound port of user module
    private final LoadUserPort loadUserPort;
    private final SaveUserPort saveUserPort;

    @Override
    public AuthProvider supports() {
        return AuthProvider.KAKAO;
    }

    @Override
    public AuthenticatedUser authenticate(AuthenticationContext context) {

        // 1. Validate context for SOCIAL
        if (!context.isValidForSocial()) {
            throw new InvalidCredentialsException(
                    "Authorization code is required for social login"
            );
        }

        // 2. Get Kakao access token
        String accessToken = kakaoAuthPort.getAccessToken(
                context.authorizationCode()
        );

        // 3. Get Kakao user info
        KakaoUserInfo kakaoInfo = kakaoAuthPort.getUserInfo(accessToken);

        // 4. Find or create user
        User user = loadUserPort.loadUserByKakaoId(kakaoInfo.kakaoId())
                .map(existingUser -> {                                      // If user is existing (Optional is not empty)
                    // Existing user - update last login
                    existingUser.updateLastLogin();
                    return existingUser;
                })
                .orElseGet(() -> {                                          // If user is not existing (Optional is empty)
                    // New user - create from Kakao info (Creator Pattern)
                    return User.createFromKakao(
                            kakaoInfo.kakaoId(),
                            kakaoInfo.email(),
                            kakaoInfo.nickname(),
                            kakaoInfo.profileImageUrl()
                    );
                });

        // If new user, the userId is null, because he hasn't saved into DB yet.
        boolean isNewUser = (user.getId() == null);

        // 5. Save user
        User savedUser = saveUserPort.saveUser(user);

        return new AuthenticatedUser(savedUser, isNewUser);
    }
}
