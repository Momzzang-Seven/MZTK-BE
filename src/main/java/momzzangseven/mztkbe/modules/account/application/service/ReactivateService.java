package momzzangseven.mztkbe.modules.account.application.service;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.InvalidCredentialsException;
import momzzangseven.mztkbe.global.error.UserNotFoundException;
import momzzangseven.mztkbe.global.error.user.AccountNotDeletedException;
import momzzangseven.mztkbe.modules.account.application.dto.AccountUserSnapshot;
import momzzangseven.mztkbe.modules.account.application.dto.GoogleUserInfo;
import momzzangseven.mztkbe.modules.account.application.dto.IssuedTokens;
import momzzangseven.mztkbe.modules.account.application.dto.KakaoUserInfo;
import momzzangseven.mztkbe.modules.account.application.dto.LoginResult;
import momzzangseven.mztkbe.modules.account.application.dto.ReactivateCommand;
import momzzangseven.mztkbe.modules.account.application.port.in.ReactivateUseCase;
import momzzangseven.mztkbe.modules.account.application.port.out.GoogleAuthPort;
import momzzangseven.mztkbe.modules.account.application.port.out.KakaoAuthPort;
import momzzangseven.mztkbe.modules.account.application.port.out.LoadAccountUserInfoPort;
import momzzangseven.mztkbe.modules.account.application.port.out.LoadUserAccountPort;
import momzzangseven.mztkbe.modules.account.application.port.out.LoadUserWalletPort;
import momzzangseven.mztkbe.modules.account.application.port.out.SaveUserAccountPort;
import momzzangseven.mztkbe.modules.account.domain.model.UserAccount;
import momzzangseven.mztkbe.modules.account.domain.vo.AuthProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class ReactivateService implements ReactivateUseCase {

  private final LoadUserAccountPort loadUserAccountPort;
  private final SaveUserAccountPort saveUserAccountPort;
  private final LoadAccountUserInfoPort loadAccountUserInfoPort;
  private final PasswordEncoder passwordEncoder;
  private final KakaoAuthPort kakaoAuthPort;
  private final GoogleAuthPort googleAuthPort;
  private final AuthTokenIssuer tokenIssuer;
  private final LoadUserWalletPort loadUserWalletPort;

  @Override
  public LoginResult execute(ReactivateCommand command) {
    command.validate();

    log.info("Reactivation request received for provider: {}", command.provider());

    AccountUserSnapshot snapshot;
    switch (command.provider()) {
      case LOCAL:
        snapshot = reactivateLocal(command.email(), command.password());
        break;
      case KAKAO:
        snapshot = reactivateKakao(command.authorizationCode());
        break;
      case GOOGLE:
        snapshot = reactivateGoogle(command.authorizationCode());
        break;
      default:
        throw new IllegalArgumentException("Unsupported provider: " + command.provider());
    }

    IssuedTokens tokens =
        tokenIssuer.issueTokens(snapshot.userId(), snapshot.email(), snapshot.role());

    String walletAddress = null;
    try {
      walletAddress = loadUserWalletPort.loadActiveWalletAddress(snapshot.userId()).orElse(null);
    } catch (Exception e) {
      log.warn(
          "Failed to load wallet address for user {}, skipping: {}",
          snapshot.userId(),
          e.getMessage());
    }

    return LoginResult.of(
        tokens.accessToken(),
        tokens.refreshToken(),
        tokens.accessTokenExpiresIn(),
        tokens.refreshTokenExpiresIn(),
        false,
        snapshot,
        walletAddress);
  }

  private AccountUserSnapshot reactivateLocal(String email, String password) {
    Optional<UserAccount> deleted = loadUserAccountPort.findDeletedByEmail(email);
    if (deleted.isPresent()) {
      UserAccount deletedAccount = deleted.get();
      verifyLocalCredentialsOrThrow(deletedAccount, password);
      saveUserAccountPort.save(deletedAccount.reactivate());
      return loadAccountUserInfoPort
          .findById(deletedAccount.getUserId())
          .orElseThrow(() -> new UserNotFoundException(email));
    }

    // if deleted user not present, find active user by its email.
    // ACTIVE user cannot be reactivated.
    if (loadUserAccountPort.findActiveByEmail(email).isPresent()) {
      throw new AccountNotDeletedException();
    }

    // Throw UserNotFoundException when either ACTIVE or DELETED is not found by email.
    throw new UserNotFoundException(email);
  }

  private AccountUserSnapshot reactivateKakao(String authorizationCode) {
    String accessToken = kakaoAuthPort.getAccessToken(authorizationCode);
    KakaoUserInfo info = kakaoAuthPort.getUserInfo(accessToken);
    return reactivateSocial(AuthProvider.KAKAO, info.getProviderUserId(), info.getEmail());
  }

  private AccountUserSnapshot reactivateGoogle(String authorizationCode) {
    String accessToken = googleAuthPort.getAccessToken(authorizationCode);
    GoogleUserInfo info = googleAuthPort.getUserInfo(accessToken);
    return reactivateSocial(AuthProvider.GOOGLE, info.getProviderUserId(), info.getEmail());
  }

  private AccountUserSnapshot reactivateSocial(
      AuthProvider provider, String providerUserId, String email) {
    Optional<UserAccount> deleted =
        loadUserAccountPort.findDeletedByProviderAndProviderUserId(provider, providerUserId);
    if (deleted.isPresent()) {
      UserAccount deletedAccount = deleted.get();
      verifyProviderInvariantOrThrow(deletedAccount, provider, providerUserId);
      saveUserAccountPort.save(deletedAccount.reactivate());
      return loadAccountUserInfoPort
          .findById(deletedAccount.getUserId())
          .orElseThrow(() -> new UserNotFoundException(email != null ? email : providerUserId));
    }

    // if deleted user not present, find active user by its provider and provider_id.
    // ACTIVE user cannot be reactivated.
    if (loadUserAccountPort.findByProviderAndProviderUserId(provider, providerUserId).isPresent()) {
      throw new AccountNotDeletedException();
    }

    // Throw UserNotFoundException when either ACTIVE or DELETED is not found by provider and
    // provider_id.
    throw new UserNotFoundException(email != null ? email : providerUserId);
  }

  private void verifyLocalCredentialsOrThrow(UserAccount account, String rawPassword) {
    if (!AuthProvider.LOCAL.equals(account.getProvider())) {
      throw new InvalidCredentialsException("Invalid email or password");
    }
    if (!passwordEncoder.matches(rawPassword, account.getPasswordHash())) {
      throw new InvalidCredentialsException("Invalid email or password");
    }
  }

  private static void verifyProviderInvariantOrThrow(
      UserAccount account, AuthProvider expectedProvider, String expectedProviderUserId) {
    if (account.getProvider() != expectedProvider
        || !expectedProviderUserId.equals(account.getProviderUserId())) {
      throw new InvalidCredentialsException("Invalid email or password");
    }
  }
}
