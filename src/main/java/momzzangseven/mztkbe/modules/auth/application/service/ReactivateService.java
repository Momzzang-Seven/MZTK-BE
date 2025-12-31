package momzzangseven.mztkbe.modules.auth.application.service;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.InvalidCredentialsException;
import momzzangseven.mztkbe.global.error.UserNotFoundException;
import momzzangseven.mztkbe.modules.auth.application.dto.GoogleUserInfo;
import momzzangseven.mztkbe.modules.auth.application.dto.KakaoUserInfo;
import momzzangseven.mztkbe.modules.auth.application.dto.LoginResult;
import momzzangseven.mztkbe.modules.auth.application.dto.ReactivateCommand;
import momzzangseven.mztkbe.modules.auth.application.port.in.ReactivateUseCase;
import momzzangseven.mztkbe.modules.auth.application.port.out.GoogleAuthPort;
import momzzangseven.mztkbe.modules.auth.application.port.out.KakaoAuthPort;
import momzzangseven.mztkbe.modules.auth.domain.model.AuthProvider;
import momzzangseven.mztkbe.modules.user.application.port.out.LoadUserPort;
import momzzangseven.mztkbe.modules.user.application.port.out.SaveUserPort;
import momzzangseven.mztkbe.modules.user.domain.model.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class ReactivateService implements ReactivateUseCase {

  private final LoadUserPort loadUserPort;
  private final SaveUserPort saveUserPort;
  private final PasswordEncoder passwordEncoder;
  private final KakaoAuthPort kakaoAuthPort;
  private final GoogleAuthPort googleAuthPort;
  private final AuthTokenIssuer tokenIssuer;

  @Override
  public LoginResult execute(ReactivateCommand command) {
    command.validate();

    log.info("Reactivation request received for provider: {}", command.provider());

    // Reactivation never creates a new user.
    // - If a DELETED user exists, restore it and issue tokens.
    // - If the user is already ACTIVE, behave like login and just issue tokens.
    User user;
    switch (command.provider()) {
      case LOCAL:
        user = reactivateLocal(command.email(), command.password());
        break;
      case KAKAO:
        user = reactivateKakao(command.authorizationCode());
        break;
      case GOOGLE:
        user = reactivateGoogle(command.authorizationCode());
        break;
      default:
        throw new IllegalArgumentException("Unsupported provider: " + command.provider());
    }

    return tokenIssuer.issue(user, false);
  }

  private User reactivateLocal(String email, String password) {
    Optional<User> deleted = loadUserPort.loadDeletedUserByEmail(email);
    if (deleted.isPresent()) {
      User deletedUser = deleted.get();
      verifyLocalCredentialsOrThrow(deletedUser, password);
      return saveUserPort.saveUser(deletedUser.reactivate());
    }

    // If already active, behave like login (issue tokens).
    User active =
        loadUserPort.loadUserByEmail(email).orElseThrow(() -> new UserNotFoundException(email));
    verifyLocalCredentialsOrThrow(active, password);
    active.updateLastLogin();
    return saveUserPort.saveUser(active);
  }

  private User reactivateKakao(String authorizationCode) {
    String accessToken = kakaoAuthPort.getAccessToken(authorizationCode);
    KakaoUserInfo info = kakaoAuthPort.getUserInfo(accessToken);
    return reactivateSocial(AuthProvider.KAKAO, info.getProviderUserId(), info.getEmail());
  }

  private User reactivateGoogle(String authorizationCode) {
    String accessToken = googleAuthPort.getAccessToken(authorizationCode);
    GoogleUserInfo info = googleAuthPort.getUserInfo(accessToken);
    return reactivateSocial(AuthProvider.GOOGLE, info.getProviderUserId(), info.getEmail());
  }

  private User reactivateSocial(AuthProvider provider, String providerUserId, String email) {
    Optional<User> deleted =
        loadUserPort.findDeletedByProviderAndProviderUserId(provider, providerUserId);
    if (deleted.isPresent()) {
      User deletedUser = deleted.get();
      verifyProviderInvariantOrThrow(deletedUser, provider);
      return saveUserPort.saveUser(deletedUser.reactivate());
    }

    // If already active, behave like login.
    User active =
        loadUserPort
            .findByProviderAndProviderUserId(provider, providerUserId)
            .orElseThrow(() -> new UserNotFoundException(email != null ? email : providerUserId));
    active.updateLastLogin();
    return saveUserPort.saveUser(active);
  }

  private void verifyLocalCredentialsOrThrow(User user, String rawPassword) {
    if (!AuthProvider.LOCAL.equals(user.getAuthProvider())) {
      throw invalidPassword();
    }
    if (!user.validatePassword(rawPassword, passwordEncoder)) {
      throw invalidPassword();
    }
  }

  private static void verifyProviderInvariantOrThrow(User user, AuthProvider expectedProvider) {
    if (user.getAuthProvider() != expectedProvider) {
      // Defensive: this should not happen (queries already filter by provider),
      // but avoid leaking internal errors as 500.
      throw invalidPassword();
    }
  }

  private static InvalidCredentialsException invalidPassword() {
    return new InvalidCredentialsException("Invalid password");
  }
}
