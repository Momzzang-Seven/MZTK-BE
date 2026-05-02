package momzzangseven.mztkbe.modules.admin.user.application.service;

import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.global.audit.domain.vo.AuditTargetType;
import momzzangseven.mztkbe.global.security.aspect.AdminOnly;
import momzzangseven.mztkbe.modules.account.domain.vo.AccountStatus;
import momzzangseven.mztkbe.modules.admin.user.application.dto.ChangeAdminUserStatusCommand;
import momzzangseven.mztkbe.modules.admin.user.application.dto.ChangeAdminUserStatusResult;
import momzzangseven.mztkbe.modules.admin.user.application.port.in.ChangeAdminUserStatusUseCase;
import momzzangseven.mztkbe.modules.admin.user.application.port.out.ChangeAdminUserAccountStatusPort;
import momzzangseven.mztkbe.modules.admin.user.application.port.out.LoadAdminUserRolePort;
import momzzangseven.mztkbe.modules.user.domain.model.UserRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Application service for admin-managed user account status changes. */
@Service
@RequiredArgsConstructor
public class ChangeAdminUserStatusService implements ChangeAdminUserStatusUseCase {

  private final LoadAdminUserRolePort loadAdminUserRolePort;
  private final ChangeAdminUserAccountStatusPort changeAdminUserAccountStatusPort;

  @Override
  @Transactional
  @AdminOnly(
      actionType = "ADMIN_USER_STATUS_CHANGE",
      targetType = AuditTargetType.USER_ACCOUNT,
      operatorId = "#command.operatorUserId",
      targetId = "#command.targetUserId")
  public ChangeAdminUserStatusResult execute(ChangeAdminUserStatusCommand command) {
    command.validate();
    validateSelfBlock(command);

    UserRole targetRole = loadAdminUserRolePort.load(command.targetUserId());
    if (targetRole.isAdmin()) {
      throw new IllegalArgumentException("Admin accounts are not managed by this API");
    }

    ChangeAdminUserAccountStatusPort.ChangeAdminUserAccountStatusResult changed =
        changeAdminUserAccountStatusPort.change(command.targetUserId(), command.status());

    // TODO(MOM-241): Revoke refresh tokens when the BLOCKED policy is finalized.
    return new ChangeAdminUserStatusResult(changed.userId(), changed.status());
  }

  private void validateSelfBlock(ChangeAdminUserStatusCommand command) {
    if (command.status() == AccountStatus.BLOCKED
        && command.operatorUserId().equals(command.targetUserId())) {
      throw new IllegalArgumentException("Cannot block own account");
    }
  }
}
