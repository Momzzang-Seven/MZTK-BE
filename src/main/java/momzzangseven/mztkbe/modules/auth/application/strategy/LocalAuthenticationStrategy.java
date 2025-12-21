package momzzangseven.mztkbe.modules.auth.application.strategy;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.error.InvalidCredentialsException;
import momzzangseven.mztkbe.global.error.UserNotFoundException;
import momzzangseven.mztkbe.modules.auth.application.dto.AuthenticatedUser;
import momzzangseven.mztkbe.modules.auth.application.dto.AuthenticationContext;
import momzzangseven.mztkbe.modules.auth.domain.model.AuthProvider;
import momzzangseven.mztkbe.modules.user.application.port.out.LoadUserPort;
import momzzangseven.mztkbe.modules.user.application.port.out.SaveUserPort;
import momzzangseven.mztkbe.modules.user.domain.model.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LocalAuthenticationStrategy implements AuthenticationStrategy {

  private final LoadUserPort loadUserPort;
  private final SaveUserPort saveUserPort;
  private final PasswordEncoder passwordEncoder;

  @Override
  public AuthProvider supports() {
    return AuthProvider.LOCAL;
  }

  @Override
  public AuthenticatedUser authenticate(AuthenticationContext context) {
    // Validate context for LOCAL
    if (!context.isValidForLocal()) {
      throw new InvalidCredentialsException(
          "Email and password are required for local authentication");
    }

    // Use email and password from context
    User user =
        loadUserPort
            .loadUserByEmail(context.email())
            .orElseThrow(() -> new UserNotFoundException(context.email()));

    // Validate password
    boolean isValid =
        user.validatePassword(
            context.password(), // raw password
            passwordEncoder);

    if (!isValid) {
      throw new InvalidCredentialsException("Invalid password");
    }

    // Update last login
    user.updateLastLogin();
    User updatedUser = saveUserPort.saveUser(user);

    // Always existing user for LOCAL
    return AuthenticatedUser.existing(updatedUser);
  }
}
