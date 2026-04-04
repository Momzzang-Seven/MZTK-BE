package momzzangseven.mztkbe.modules.account.application.strategy;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.InvalidCredentialsException;
import momzzangseven.mztkbe.global.error.UserNotFoundException;
import momzzangseven.mztkbe.global.error.user.UserWithdrawnException;
import momzzangseven.mztkbe.modules.account.application.dto.AccountUserSnapshot;
import momzzangseven.mztkbe.modules.account.application.dto.AuthenticatedUser;
import momzzangseven.mztkbe.modules.account.application.dto.AuthenticationContext;
import momzzangseven.mztkbe.modules.account.application.port.out.LoadAccountUserInfoPort;
import momzzangseven.mztkbe.modules.account.application.port.out.LoadUserAccountPort;
import momzzangseven.mztkbe.modules.account.application.port.out.SaveUserAccountPort;
import momzzangseven.mztkbe.modules.account.domain.model.UserAccount;
import momzzangseven.mztkbe.modules.account.domain.vo.AuthProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LocalAuthenticationStrategy implements AuthenticationStrategy {

  private final LoadUserAccountPort loadUserAccountPort;
  private final SaveUserAccountPort saveUserAccountPort;
  private final LoadAccountUserInfoPort loadAccountUserInfoPort;
  private final PasswordEncoder passwordEncoder;

  @Override
  public AuthProvider supports() {
    return AuthProvider.LOCAL;
  }

  @Override
  public AuthenticatedUser authenticate(AuthenticationContext context) {
    validateContext(context);

    UserAccount account = resolveAccount(context.email());
    verifyLocalProvider(account);
    verifyPassword(context.password(), account.getPasswordHash());

    if (account.isDeleted()) {
      throw new UserWithdrawnException();
    }

    return completeLogin(account);
  }

  private void validateContext(AuthenticationContext context) {
    if (!context.isValidForLocal()) {
      throw new InvalidCredentialsException(
          "Email and password are required for local authentication");
    }
  }

  private UserAccount resolveAccount(String email) {
    return loadUserAccountPort
        .findActiveByEmail(email)
        .or(() -> loadUserAccountPort.findDeletedByEmail(email))
        .orElseThrow(() -> new UserNotFoundException(email));
  }

  private void verifyLocalProvider(UserAccount account) {
    if (!AuthProvider.LOCAL.equals(account.getProvider())) {
      throw new InvalidCredentialsException("Invalid provider");
    }
  }

  private void verifyPassword(String rawPassword, String encodedPassword) {
    if (!passwordEncoder.matches(rawPassword, encodedPassword)) {
      throw new InvalidCredentialsException("Invalid email or password");
    }
  }

  private AuthenticatedUser completeLogin(UserAccount account) {
    saveUserAccountPort.save(account.updateLastLogin());
    AccountUserSnapshot snapshot =
        loadAccountUserInfoPort
            .findById(account.getUserId())
            .orElseThrow(() -> new UserNotFoundException(account.getUserId()));
    return AuthenticatedUser.existing(snapshot);
  }
}
