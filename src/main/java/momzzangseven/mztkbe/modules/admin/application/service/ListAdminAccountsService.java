package momzzangseven.mztkbe.modules.admin.application.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.audit.domain.vo.AuditTargetType;
import momzzangseven.mztkbe.global.security.aspect.AdminOnly;
import momzzangseven.mztkbe.modules.admin.application.dto.AdminAccountSummary;
import momzzangseven.mztkbe.modules.admin.application.port.in.ListAdminAccountsUseCase;
import momzzangseven.mztkbe.modules.admin.application.port.out.LoadAdminAccountPort;
import momzzangseven.mztkbe.modules.admin.domain.model.AdminAccount;
import momzzangseven.mztkbe.modules.user.application.port.out.LoadUserPort;
import momzzangseven.mztkbe.modules.user.domain.model.User;
import momzzangseven.mztkbe.modules.user.domain.model.UserRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Service for listing all active admin accounts. */
@Service
@RequiredArgsConstructor
public class ListAdminAccountsService implements ListAdminAccountsUseCase {

  private final LoadAdminAccountPort loadAdminAccountPort;
  private final LoadUserPort loadUserPort;

  @Override
  @Transactional(readOnly = true)
  @AdminOnly(actionType = "LIST_ADMINS", targetType = AuditTargetType.ADMIN_ACCOUNT)
  public List<AdminAccountSummary> execute(Long operatorUserId) {
    List<AdminAccount> accounts = loadAdminAccountPort.findAllActive();
    List<Long> userIds = accounts.stream().map(AdminAccount::getUserId).toList();
    List<User> users = loadUserPort.loadUsersByIds(userIds);

    return accounts.stream()
        .map(
            account -> {
              boolean isSeed =
                  users.stream()
                      .filter(u -> u.getId().equals(account.getUserId()))
                      .findFirst()
                      .map(u -> u.getRole() == UserRole.ADMIN_SEED)
                      .orElse(false);

              return new AdminAccountSummary(
                  account.getUserId(),
                  account.getLoginId(),
                  isSeed,
                  account.getCreatedBy(),
                  account.getLastLoginAt(),
                  account.getPasswordLastRotatedAt());
            })
        .toList();
  }
}
