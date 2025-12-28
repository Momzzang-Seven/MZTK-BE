package momzzangseven.mztkbe.modules.user.application.service;

import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.auth.domain.model.AuthProvider;
import momzzangseven.mztkbe.modules.user.application.port.in.SocialLoginOutcome;
import momzzangseven.mztkbe.modules.user.application.port.in.SocialLoginUseCase;
import momzzangseven.mztkbe.modules.user.application.port.out.LoadUserPort;
import momzzangseven.mztkbe.modules.user.application.port.out.SaveUserPort;
import momzzangseven.mztkbe.modules.user.domain.model.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService implements SocialLoginUseCase {

  private final LoadUserPort loadUserPort;
  private final SaveUserPort saveUserPort;

  /**
   * Handles social login or registration.
   *
   * <p>If the user exists, updates the last login time. If not, creates a new user. If the nickname
   * is missing, generates a default one.
   *
   * @param provider the social provider name (e.g., "KAKAO", "GOOGLE")
   * @param providerUserId the unique user ID from the provider
   * @param email the user's email address
   * @param nickname the user's nickname (optional, will be generated if null/blank)
   * @param profileImageUrl the user's profile image URL
   * @return the result of the social login, containing the user and new user flag
   * @throws IllegalArgumentException if provider or providerUserId is invalid
   * @throws IllegalStateException if email is missing or account exists with different provider
   */
  @Override
  @Transactional
  public SocialLoginOutcome loginOrRegisterSocial(
      String provider,
      String providerUserId,
      String email,
      String nickname,
      String profileImageUrl) {

    if (provider == null || provider.isBlank()) {
      throw new IllegalArgumentException("provider is required");
    }
    // Convert provider string to AuthProvider enum for internal use
    AuthProvider authProvider;
    try {
      authProvider = AuthProvider.valueOf(provider);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Unsupported social provider: " + provider, e);
    }

    if (providerUserId == null || providerUserId.isBlank()) {
      throw new IllegalArgumentException("providerUserId is required");
    }

    if (email == null || email.isBlank()) {
      throw new IllegalStateException("email is required for social login");
    }

    Optional<User> byProvider =
        loadUserPort.findByProviderAndProviderUserId(authProvider, providerUserId);

    if (byProvider.isPresent()) {
      User user = byProvider.get();
      user.updateLastLogin();
      User updatedUser = saveUserPort.saveUser(user);
      return SocialLoginOutcome.existing(updatedUser);
    }

    Optional<User> byEmail = loadUserPort.loadUserByEmail(email);
    if (byEmail.isPresent()) {
      User existing = byEmail.get();

      if (existing.getAuthProvider() != authProvider) {
        throw new IllegalStateException(
            "Account already exists with a different provider. Email=" + email);
      }

      throw new IllegalStateException("Invalid social login state: providerUserId mismatch");
    }

    // Generate default nickname if missing
    String finalNickname = nickname;
    if (finalNickname == null || finalNickname.isBlank()) {
      finalNickname =
          authProvider.name().toLowerCase() + "_" + UUID.randomUUID().toString().substring(0, 8);
    }

    User created =
        User.createFromSocial(authProvider, providerUserId, email, finalNickname, profileImageUrl);

    User saved = saveUserPort.saveUser(created);
    return SocialLoginOutcome.newUser(saved);
  }
}
