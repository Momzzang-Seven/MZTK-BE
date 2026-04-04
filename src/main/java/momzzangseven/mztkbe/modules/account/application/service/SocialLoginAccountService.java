package momzzangseven.mztkbe.modules.account.application.service;

import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.InvalidCredentialsException;
import momzzangseven.mztkbe.global.error.user.UserWithdrawnException;
import momzzangseven.mztkbe.modules.account.application.dto.AccountUserSnapshot;
import momzzangseven.mztkbe.modules.account.application.dto.SocialLoginAccountOutcome;
import momzzangseven.mztkbe.modules.account.application.port.out.CreateAccountUserPort;
import momzzangseven.mztkbe.modules.account.application.port.out.LoadAccountUserInfoPort;
import momzzangseven.mztkbe.modules.account.application.port.out.LoadUserAccountPort;
import momzzangseven.mztkbe.modules.account.application.port.out.SaveUserAccountPort;
import momzzangseven.mztkbe.modules.account.domain.model.UserAccount;
import momzzangseven.mztkbe.modules.account.domain.vo.AuthProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class SocialLoginAccountService {

  private final LoadUserAccountPort loadUserAccountPort;
  private final SaveUserAccountPort saveUserAccountPort;
  private final LoadAccountUserInfoPort loadAccountUserInfoPort;
  private final CreateAccountUserPort createAccountUserPort;

  /**
   * Finds an existing account or registers a new one for the given social identity.
   *
   * <p>{@code findByProviderAndProviderUserId} returns accounts of any status, so a single query
   * covers both the active-login and the deleted-account cases without a second DB call.
   */
  public SocialLoginAccountOutcome loginOrRegister(
      AuthProvider provider,
      String providerUserId,
      String email,
      String nickname,
      String profileImageUrl) {

    Optional<UserAccount> existing =
        loadUserAccountPort.findByProviderAndProviderUserId(provider, providerUserId);

    if (existing.isPresent()) {
      return handleExistingAccount(existing.get(), provider, providerUserId);
    }

    return registerNewUser(provider, providerUserId, email, nickname, profileImageUrl);
  }

  /**
   * Handles the case where an account already exists for the given provider identity.
   *
   * @throws UserWithdrawnException if the account is soft-deleted
   * @throws InvalidCredentialsException if the user profile cannot be found
   */
  private SocialLoginAccountOutcome handleExistingAccount(
      UserAccount account, AuthProvider authProvider, String providerUserId) {
    if (account.isDeleted()) {
      log.info(
          "Withdrawn social account login attempt: provider={}, providerUserId={}",
          authProvider,
          providerUserId);
      throw new UserWithdrawnException();
    }
    saveUserAccountPort.save(account.updateLastLogin());
    AccountUserSnapshot snapshot =
        loadAccountUserInfoPort
            .findById(account.getUserId())
            .orElseThrow(() -> new InvalidCredentialsException("Invalid social login"));
    return SocialLoginAccountOutcome.existing(snapshot);
  }

  /**
   * Registers a brand-new user and account for the given social identity.
   *
   * @throws InvalidCredentialsException if the email is already taken by an active user
   * @throws UserWithdrawnException if the email belongs to a soft-deleted account
   */
  private SocialLoginAccountOutcome registerNewUser(
      AuthProvider authProvider,
      String providerUserId,
      String email,
      String nickname,
      String profileImageUrl) {
    verifyEmailAvailable(email);
    String resolvedNickname = resolveNickname(authProvider, nickname);
    AccountUserSnapshot snapshot =
        createAccountUserPort.createUser(email, resolvedNickname, profileImageUrl, "USER");
    saveUserAccountPort.save(
        UserAccount.createSocial(snapshot.userId(), authProvider, providerUserId));
    return SocialLoginAccountOutcome.newUser(snapshot);
  }

  /**
   * Ensures the email is not taken by an active user or reserved by a soft-deleted account.
   *
   * <p>Uses {@code existsByEmail} (existence check only) instead of a full snapshot load to avoid
   * fetching unnecessary data.
   */
  private void verifyEmailAvailable(String email) {
    if (loadAccountUserInfoPort.existsByEmail(email)) {
      throw new InvalidCredentialsException("Invalid social login");
    }
    if (loadUserAccountPort.findDeletedByEmail(email).isPresent()) {
      log.info("Withdrawn social account login attempt: email={}", email);
      throw new UserWithdrawnException();
    }
  }

  /** Returns a non-blank nickname, generating a random one if the caller supplied none. */
  private String resolveNickname(AuthProvider authProvider, String nickname) {
    if (nickname != null && !nickname.isBlank()) {
      return nickname;
    }
    return authProvider.name().toLowerCase() + "_" + UUID.randomUUID().toString().substring(0, 8);
  }
}
