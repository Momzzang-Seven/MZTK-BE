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

  private static final String DEFAULT_ROLE = "USER";

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
      String profileImageUrl,
      String role) {

    Optional<UserAccount> existing =
        loadUserAccountPort.findByProviderAndProviderUserId(provider, providerUserId);

    // If users_account row already exists -> login phase. ignore the role field of command.
    if (existing.isPresent()) {
      return handleExistingAccount(existing.get(), provider, providerUserId);
    }

    // When register new user, must consider role field.
    return registerNewUser(provider, providerUserId, email, nickname, profileImageUrl, role);
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
      String profileImageUrl,
      String role) {
    verifyEmailAvailable(email);
    String resolvedNickname = resolveNickname(authProvider, nickname);
    String effectiveRole = resolveRole(role);
    AccountUserSnapshot snapshot =
        createAccountUserPort.createUser(email, resolvedNickname, profileImageUrl, effectiveRole);
    saveUserAccountPort.save(
        UserAccount.createSocial(snapshot.userId(), authProvider, providerUserId));
    return SocialLoginAccountOutcome.newUser(snapshot);
  }

  /**
   * Ensures the email is not taken by an active user or reserved by a soft-deleted account.
   *
   * <p>Checks for a soft-deleted account first so that withdrawn users receive {@link
   * UserWithdrawnException} and can be redirected to {@code /auth/reactivate}. Only then checks for
   * active users; {@code existsByEmail} has no soft-delete filter, so checking it first would
   * incorrectly surface deleted accounts as active conflicts.
   */
  private void verifyEmailAvailable(String email) {
    if (loadUserAccountPort.findDeletedByEmail(email).isPresent()) {
      log.info("Withdrawn social account login attempt: email={}", email);
      throw new UserWithdrawnException();
    }
    if (loadAccountUserInfoPort.existsByEmail(email)) {
      throw new InvalidCredentialsException("Invalid social login");
    }
  }

  /** Returns the given role or falls back to {@code "USER"} when {@code null}. */
  private String resolveRole(String role) {
    return role == null ? DEFAULT_ROLE : role;
  }

  /** Returns a non-blank nickname, generating a random one if the caller supplied none. */
  private String resolveNickname(AuthProvider authProvider, String nickname) {
    if (nickname != null && !nickname.isBlank()) {
      return nickname;
    }
    return authProvider.name().toLowerCase() + "_" + UUID.randomUUID().toString().substring(0, 8);
  }
}
