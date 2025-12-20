package momzzangseven.mztkbe.modules.auth.application.strategy;

import momzzangseven.mztkbe.modules.auth.application.dto.AuthenticatedUser;
import momzzangseven.mztkbe.modules.auth.application.dto.AuthenticationContext;
import momzzangseven.mztkbe.modules.auth.domain.model.AuthProvider;

public interface AuthenticationStrategy {
  AuthProvider supports();

  AuthenticatedUser authenticate(AuthenticationContext context);
}
