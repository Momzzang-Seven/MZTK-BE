package momzzangseven.mztkbe.modules.account.application.strategy;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.InvalidCredentialsException;
import momzzangseven.mztkbe.modules.account.application.dto.AccountUserSnapshot;
import momzzangseven.mztkbe.modules.account.application.dto.AuthenticatedUser;
import momzzangseven.mztkbe.modules.account.application.dto.AuthenticationContext;
import momzzangseven.mztkbe.modules.account.application.port.out.AdminLocalAuthPort;
import momzzangseven.mztkbe.modules.account.application.port.out.LoadAccountUserInfoPort;
import momzzangseven.mztkbe.modules.account.domain.vo.AuthProvider;
import org.springframework.stereotype.Component;

/**
 * Authentication strategy for admin local login (LOGIN_ID + password). Delegates credential
 * verification to the admin module via {@link AdminLocalAuthPort} and resolves the user profile
 * through {@link LoadAccountUserInfoPort}.
 */
@Component
@RequiredArgsConstructor
public class LocalAdminAuthenticationStrategy implements AuthenticationStrategy {

  private final AdminLocalAuthPort adminLocalAuthPort;
  private final LoadAccountUserInfoPort loadAccountUserInfoPort;

  @Override
  public AuthProvider supports() {
    return AuthProvider.LOCAL_ADMIN;
  }

  @Override
  public AuthenticatedUser authenticate(AuthenticationContext context) {
    if (!context.isValidForLocalAdmin()) {
      throw new InvalidCredentialsException(
          "LoginId and password are required for admin authentication");
    }

    Long userId =
        adminLocalAuthPort.authenticateAndGetUserId(context.loginId(), context.password());

    AccountUserSnapshot snapshot =
        loadAccountUserInfoPort
            .findById(userId)
            .orElseThrow(() -> new InvalidCredentialsException("Admin user not found"));

    return AuthenticatedUser.existing(snapshot);
  }
}
