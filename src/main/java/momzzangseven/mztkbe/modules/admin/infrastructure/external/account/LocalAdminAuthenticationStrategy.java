package momzzangseven.mztkbe.modules.admin.infrastructure.external.account;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.InvalidCredentialsException;
import momzzangseven.mztkbe.modules.account.application.dto.AccountUserSnapshot;
import momzzangseven.mztkbe.modules.account.application.dto.AuthenticatedUser;
import momzzangseven.mztkbe.modules.account.application.dto.AuthenticationContext;
import momzzangseven.mztkbe.modules.account.application.strategy.AuthenticationStrategy;
import momzzangseven.mztkbe.modules.account.domain.vo.AuthProvider;
import momzzangseven.mztkbe.modules.admin.application.port.out.AdminPasswordEncoderPort;
import momzzangseven.mztkbe.modules.admin.application.port.out.LoadAdminAccountPort;
import momzzangseven.mztkbe.modules.admin.application.port.out.SaveAdminAccountPort;
import momzzangseven.mztkbe.modules.admin.domain.model.AdminAccount;
import momzzangseven.mztkbe.modules.user.application.port.out.LoadUserPort;
import momzzangseven.mztkbe.modules.user.domain.model.User;
import org.springframework.stereotype.Component;

/**
 * Authentication strategy for admin local login (LOGIN_ID + password). Queries the {@code
 * admin_accounts} table and verifies BCrypt-hashed credentials. Located in the admin module but
 * implements the account module's AuthenticationStrategy interface (cross-module adapter).
 */
@Component
@RequiredArgsConstructor
public class LocalAdminAuthenticationStrategy implements AuthenticationStrategy {

  private final LoadAdminAccountPort loadAdminAccountPort;
  private final AdminPasswordEncoderPort adminPasswordEncoderPort;
  private final SaveAdminAccountPort saveAdminAccountPort;
  private final LoadUserPort loadUserPort;

  @Override
  public AuthProvider supports() {
    return AuthProvider.LOCAL_ADMIN;
  }

  @Override
  public AuthenticatedUser authenticate(AuthenticationContext context) {
    AdminAccount account =
        loadAdminAccountPort
            .findActiveByLoginId(context.loginId())
            .orElseThrow(() -> new InvalidCredentialsException("Invalid admin credentials"));

    if (!adminPasswordEncoderPort.matches(context.password(), account.getPasswordHash())) {
      throw new InvalidCredentialsException("Invalid admin credentials");
    }

    saveAdminAccountPort.save(account.updateLastLogin());

    User user =
        loadUserPort
            .loadUserById(account.getUserId())
            .orElseThrow(() -> new InvalidCredentialsException("Admin user not found"));

    AccountUserSnapshot snapshot =
        new AccountUserSnapshot(
            user.getId(),
            user.getEmail(),
            user.getNickname(),
            user.getProfileImageUrl(),
            user.getRole().name());

    return AuthenticatedUser.existing(snapshot);
  }
}
