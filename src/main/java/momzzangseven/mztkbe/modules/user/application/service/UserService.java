package momzzangseven.mztkbe.modules.user.application.service;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.user.UserWithdrawnException;
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
      saveUserPort.saveUser(user);
      return SocialLoginOutcome.existing(user);
    }

    verifyNotWithdrawnByProvider(authProvider, providerUserId);

    Optional<User> byEmail = loadUserPort.loadUserByEmail(email);
    if (byEmail.isPresent()) {
      User existing = byEmail.get();

      if (existing.getAuthProvider() != authProvider) {
        throw new IllegalStateException(
            "Account already exists with a different provider. Email=" + email);
      }

      throw new IllegalStateException("Invalid social login state: providerUserId mismatch");
    }

    verifyNotWithdrawnByEmail(email);

    if (nickname == null || nickname.isBlank()) {
      nickname =
          provider.toLowerCase() + "_" + java.util.UUID.randomUUID().toString().substring(0, 8);
    }

    User created;
    if (authProvider == AuthProvider.KAKAO) {
      created = User.createFromKakao(providerUserId, email, nickname, profileImageUrl);
    } else if (authProvider == AuthProvider.GOOGLE) {
      created = User.createFromGoogle(providerUserId, email, nickname, profileImageUrl);
    } else {
      throw new IllegalArgumentException("Unsupported social provider: " + authProvider);
    }

    User saved = saveUserPort.saveUser(created);
    return SocialLoginOutcome.newUser(saved);
  }

  private void verifyNotWithdrawnByProvider(AuthProvider authProvider, String providerUserId) {
    Optional<User> deletedByProvider =
        loadUserPort.findDeletedByProviderAndProviderUserId(authProvider, providerUserId);
    if (deletedByProvider.isPresent()) {
      log.info(
          "Withdrawn social account login attempt: provider={}, providerUserId={}",
          authProvider,
          providerUserId);
      throw new UserWithdrawnException();
    }
  }

  private void verifyNotWithdrawnByEmail(String email) {
    Optional<User> deletedByEmail = loadUserPort.loadDeletedUserByEmail(email);
    if (deletedByEmail.isPresent()) {
      log.info("Withdrawn social account login attempt: email={}", email);
      throw new UserWithdrawnException();
    }
  }
}
