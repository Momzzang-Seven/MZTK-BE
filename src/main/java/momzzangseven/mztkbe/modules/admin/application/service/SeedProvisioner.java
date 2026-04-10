package momzzangseven.mztkbe.modules.admin.application.service;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.error.admin.AdminCredentialGenerationException;
import momzzangseven.mztkbe.modules.admin.application.port.out.AdminPasswordEncoderPort;
import momzzangseven.mztkbe.modules.admin.application.port.out.CreateAdminUserPort;
import momzzangseven.mztkbe.modules.admin.application.port.out.GenerateCredentialPort;
import momzzangseven.mztkbe.modules.admin.application.port.out.LoadAdminAccountPort;
import momzzangseven.mztkbe.modules.admin.application.port.out.SaveAdminAccountPort;
import momzzangseven.mztkbe.modules.admin.domain.model.AdminAccount;
import momzzangseven.mztkbe.modules.admin.domain.vo.GeneratedAdminCredentials;
import momzzangseven.mztkbe.modules.user.domain.model.UserRole;
import org.springframework.stereotype.Component;

/**
 * Package-private helper that provisions admin accounts. Used by both BootstrapSeedAdminsService
 * and RecoveryReseedService.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class SeedProvisioner {

  private static final int MAX_RETRIES = 5;

  private final GenerateCredentialPort generateCredentialPort;
  private final CreateAdminUserPort createAdminUserPort;
  private final AdminPasswordEncoderPort adminPasswordEncoderPort;
  private final SaveAdminAccountPort saveAdminAccountPort;
  private final LoadAdminAccountPort loadAdminAccountPort;

  /**
   * Provision the given number of admin accounts.
   *
   * @param count the number of accounts to create
   * @param role the admin role (ADMIN_SEED or ADMIN_GENERATED)
   * @return list of generated credentials (loginId + plaintext)
   */
  List<GeneratedAdminCredentials> provision(int count, UserRole role) {
    List<GeneratedAdminCredentials> result = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      GeneratedAdminCredentials credentials = generateUniqueCredentials();

      String email = buildEmail(role, credentials.loginId(), i + 1);
      String nickname = buildNickname(role, credentials.loginId(), i + 1);

      Long userId = createAdminUserPort.createAdmin(email, nickname, role);
      String passwordHash = adminPasswordEncoderPort.encode(credentials.plaintext());

      AdminAccount account = AdminAccount.create(userId, credentials.loginId(), passwordHash, null);
      saveAdminAccountPort.save(account);

      result.add(credentials);
      log.info("Provisioned admin: role={}, loginId={}", role, credentials.loginId());
    }
    return result;
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

  private String buildEmail(UserRole role, String loginId, int ordinal) {
    if (role == UserRole.ADMIN_SEED) {
      return "seed-admin-" + ordinal + "@internal.mztk.local";
    }
    return "admin-" + loginId + "@internal.mztk.local";
  }

  private String buildNickname(UserRole role, String loginId, int ordinal) {
    if (role == UserRole.ADMIN_SEED) {
      return "SeedAdmin-" + ordinal;
    }
    return "Admin-" + loginId;
  }
}
