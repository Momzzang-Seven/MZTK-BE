package momzzangseven.mztkbe.modules.account.application.strategy;

import momzzangseven.mztkbe.modules.account.application.dto.AuthenticatedUser;
import momzzangseven.mztkbe.modules.account.application.dto.AuthenticationContext;
import momzzangseven.mztkbe.modules.account.domain.vo.AuthProvider;

/** Strategy contract for provider-specific authentication flows. */
public interface AuthenticationStrategy {
  /** Authenticate user with given context. Returns authenticated user information. */
  AuthenticatedUser authenticate(AuthenticationContext context);

  /** Return supporting provider. */
  AuthProvider supports();
}
