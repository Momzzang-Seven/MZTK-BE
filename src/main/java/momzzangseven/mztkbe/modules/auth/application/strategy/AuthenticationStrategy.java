package momzzangseven.mztkbe.modules.auth.application.strategy;

import momzzangseven.mztkbe.modules.auth.application.dto.AuthenticatedUser;
import momzzangseven.mztkbe.modules.auth.application.dto.AuthenticationContext;
import momzzangseven.mztkbe.modules.auth.domain.model.AuthProvider;

/**
 * Strategy contract for provider-specific authentication flows.
 */
public interface AuthenticationStrategy {
  /** Authenticate user with given context. Returns authenticated user information. */
  AuthenticatedUser authenticate(AuthenticationContext context);

  /** Return supporting provider. */
  AuthProvider supports();
}
