package momzzangseven.mztkbe.modules.admin.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.audit.domain.vo.AuditTargetType;
import momzzangseven.mztkbe.global.error.admin.AdminCredentialGenerationException;
import momzzangseven.mztkbe.global.security.aspect.AdminOnly;
import momzzangseven.mztkbe.modules.admin.application.dto.CreateAdminAccountResult;
import momzzangseven.mztkbe.modules.admin.application.port.in.CreateAdminAccountUseCase;
import momzzangseven.mztkbe.modules.admin.application.port.out.AdminPasswordEncoderPort;
import momzzangseven.mztkbe.modules.admin.application.port.out.CreateAdminUserPort;
import momzzangseven.mztkbe.modules.admin.application.port.out.GenerateCredentialPort;
import momzzangseven.mztkbe.modules.admin.application.port.out.LoadAdminAccountPort;
import momzzangseven.mztkbe.modules.admin.application.port.out.SaveAdminAccountPort;
import momzzangseven.mztkbe.modules.admin.domain.model.AdminAccount;
import momzzangseven.mztkbe.modules.admin.domain.vo.AdminRole;
import momzzangseven.mztkbe.modules.admin.domain.vo.GeneratedAdminCredentials;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Service for creating a new admin account (ADMIN_GENERATED). */
@Service
@RequiredArgsConstructor
public class CreateAdminAccountService implements CreateAdminAccountUseCase {

  private static final int MAX_RETRIES = 5;

  private final GenerateCredentialPort generateCredentialPort;
  private final CreateAdminUserPort createAdminUserPort;
  private final AdminPasswordEncoderPort adminPasswordEncoderPort;
  private final SaveAdminAccountPort saveAdminAccountPort;
  private final LoadAdminAccountPort loadAdminAccountPort;

  @Override
  @Transactional
  @AdminOnly(
      actionType = "CREATE_ADMIN",
      targetType = AuditTargetType.ADMIN_ACCOUNT,
      targetId = "#result.userId")
  public CreateAdminAccountResult execute(Long operatorUserId) {
    GeneratedAdminCredentials credentials = generateUniqueCredentials();

    String email = "admin-" + credentials.loginId() + "@internal.mztk.local";
    String nickname = "Admin-" + credentials.loginId();

    Long userId = createAdminUserPort.createAdmin(email, nickname, AdminRole.ADMIN_GENERATED);

    String passwordHash = adminPasswordEncoderPort.encode(credentials.plaintext());

    AdminAccount account =
        AdminAccount.create(userId, credentials.loginId(), passwordHash, operatorUserId);
    AdminAccount saved = saveAdminAccountPort.save(account);

    return new CreateAdminAccountResult(
        saved.getUserId(), saved.getLoginId(), credentials.plaintext(), saved.getCreatedAt());
  }

  private GeneratedAdminCredentials generateUniqueCredentials() {
    for (int i = 0; i < MAX_RETRIES; i++) {
      GeneratedAdminCredentials credentials = generateCredentialPort.generate();
      if (!loadAdminAccountPort.existsByLoginId(credentials.loginId())) {
        return credentials;
      }
    }
    throw new AdminCredentialGenerationException(
        "Failed to generate unique loginId after " + MAX_RETRIES + " attempts");
  }
}
