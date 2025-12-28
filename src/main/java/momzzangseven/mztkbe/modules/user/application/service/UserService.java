package momzzangseven.mztkbe.modules.user.application.service;

import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.BusinessException;
import momzzangseven.mztkbe.global.error.ErrorCode;
import momzzangseven.mztkbe.modules.auth.domain.model.AuthProvider;
import momzzangseven.mztkbe.modules.user.application.port.in.SocialLoginOutcome;
import momzzangseven.mztkbe.modules.user.application.port.in.SocialLoginUseCase;
import momzzangseven.mztkbe.modules.user.application.port.out.LoadUserPort;
import momzzangseven.mztkbe.modules.user.application.port.out.SaveUserPort;
import momzzangseven.mztkbe.modules.user.domain.model.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
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
   * @throws BusinessException if provider, email, or social ID state is invalid
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
      throw new BusinessException(ErrorCode.MISSING_REQUIRED_FIELD, "provider is required");
    }

    // Convert provider string to AuthProvider enum (Case-insensitive)
    AuthProvider authProvider;
    try {
      authProvider = AuthProvider.valueOf(provider.toUpperCase());
    } catch (IllegalArgumentException e) {
      log.error("Unsupported social provider requested: {}", provider);
      throw new BusinessException(
          ErrorCode.UNSUPPORTED_PROVIDER, "Unsupported social provider: " + provider);
    }

    if (providerUserId == null || providerUserId.isBlank()) {
      throw new BusinessException(ErrorCode.MISSING_REQUIRED_FIELD, "providerUserId is required");
    }

    if (email == null || email.isBlank()) {
      throw new BusinessException(
          ErrorCode.MISSING_REQUIRED_FIELD, "email is required for social login");
    }

    // Standardize email to lowercase for consistent lookup
    String normalizedEmail = email.toLowerCase();

    Optional<User> byProvider =
        loadUserPort.findByProviderAndProviderUserId(authProvider, providerUserId);

    if (byProvider.isPresent()) {
      User user = byProvider.get();
      user.updateLastLogin();
      User updatedUser = saveUserPort.saveUser(user);
      return SocialLoginOutcome.existing(updatedUser);
    }

    // Check if email already exists with different provider or same provider but different ID
    Optional<User> byEmail = loadUserPort.loadUserByEmail(normalizedEmail);
    if (byEmail.isPresent()) {
      User existing = byEmail.get();

      if (existing.getAuthProvider() != authProvider) {
        log.warn(
            "Social login conflict: Email {} is already registered with provider {}",
            normalizedEmail,
            existing.getAuthProvider());
        throw new BusinessException(
            ErrorCode.DUPLICATE_EMAIL,
            String.format(
                "The email %s is already associated with a %s account. Please use that to login.",
                normalizedEmail, existing.getAuthProvider()));
      }

      log.error(
          "Social login ID mismatch: Email {} exists for {} but providerUserId differs. Existing={}, Attempted={}",
          normalizedEmail,
          authProvider,
          existing.getProviderUserId(),
          providerUserId);
      throw new BusinessException(
          ErrorCode.INTERNAL_SERVER_ERROR,
          "An account with this email already exists, but the social IDs do not match. Please contact support.");
    }

    // Handle missing nickname - Generation logic moved from Domain to Application Service
    String finalNickname = nickname;
    if (finalNickname == null || finalNickname.isBlank()) {
      finalNickname = generateDefaultNickname(authProvider);
    }

    User created =
        User.createFromSocial(
            authProvider, providerUserId, normalizedEmail, finalNickname, profileImageUrl);

    User saved = saveUserPort.saveUser(created);
    log.info("New social user registered: {} via {}", normalizedEmail, authProvider);
    return SocialLoginOutcome.newUser(saved);
  }

  /** Generates a default random nickname based on the provider. */
  private String generateDefaultNickname(AuthProvider provider) {
    return provider.name().toLowerCase() + "_" + UUID.randomUUID().toString().substring(0, 8);
  }
}
