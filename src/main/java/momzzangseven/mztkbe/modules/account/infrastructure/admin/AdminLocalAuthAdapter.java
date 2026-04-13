package momzzangseven.mztkbe.modules.account.infrastructure.admin;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.account.application.port.out.AdminLocalAuthPort;
import momzzangseven.mztkbe.modules.admin.application.dto.AuthenticateAdminLocalCommand;
import momzzangseven.mztkbe.modules.admin.application.dto.AuthenticateAdminLocalResult;
import momzzangseven.mztkbe.modules.admin.application.port.in.AuthenticateAdminLocalUseCase;
import org.springframework.stereotype.Component;

/**
 * Infrastructure adapter bridging the account module's {@link AdminLocalAuthPort} to the admin
 * module's {@link AuthenticateAdminLocalUseCase}. This is the only class in the account module that
 * imports from the admin module.
 */
@Component
@RequiredArgsConstructor
public class AdminLocalAuthAdapter implements AdminLocalAuthPort {

  private final AuthenticateAdminLocalUseCase authenticateAdminLocalUseCase;

  @Override
  public Long authenticateAndGetUserId(String loginId, String password) {
    AuthenticateAdminLocalResult result =
        authenticateAdminLocalUseCase.execute(new AuthenticateAdminLocalCommand(loginId, password));
    return result.userId();
  }
}
