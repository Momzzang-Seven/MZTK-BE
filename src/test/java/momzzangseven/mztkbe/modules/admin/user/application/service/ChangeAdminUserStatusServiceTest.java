package momzzangseven.mztkbe.modules.admin.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import momzzangseven.mztkbe.modules.admin.user.application.dto.ChangeAdminUserStatusCommand;
import momzzangseven.mztkbe.modules.admin.user.application.port.out.ChangeAdminUserAccountStatusPort;
import momzzangseven.mztkbe.modules.admin.user.application.port.out.LoadAdminUserRolePort;
import momzzangseven.mztkbe.modules.admin.user.domain.vo.AdminUserAccountStatus;
import momzzangseven.mztkbe.modules.admin.user.domain.vo.AdminUserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChangeAdminUserStatusService 단위 테스트")
class ChangeAdminUserStatusServiceTest {

  @Mock private LoadAdminUserRolePort loadAdminUserRolePort;
  @Mock private ChangeAdminUserAccountStatusPort changeAdminUserAccountStatusPort;

  @InjectMocks private ChangeAdminUserStatusService service;

  @Test
  @DisplayName("ACTIVE/BLOCKED 대상 사용자를 BLOCKED 로 변경한다")
  void execute_blocksUser() {
    given(loadAdminUserRolePort.load(21L)).willReturn(AdminUserRole.USER);
    given(changeAdminUserAccountStatusPort.change(21L, AdminUserAccountStatus.BLOCKED))
        .willReturn(
            new ChangeAdminUserAccountStatusPort.ChangeAdminUserAccountStatusResult(
                21L, AdminUserAccountStatus.BLOCKED));

    var result =
        service.execute(
            new ChangeAdminUserStatusCommand(9L, 21L, AdminUserAccountStatus.BLOCKED, "spam"));

    assertThat(result.userId()).isEqualTo(21L);
    assertThat(result.status()).isEqualTo(AdminUserAccountStatus.BLOCKED);
  }

  @Test
  @DisplayName("BLOCKED 사용자를 ACTIVE 로 변경한다")
  void execute_activatesUser() {
    given(loadAdminUserRolePort.load(21L)).willReturn(AdminUserRole.TRAINER);
    given(changeAdminUserAccountStatusPort.change(21L, AdminUserAccountStatus.ACTIVE))
        .willReturn(
            new ChangeAdminUserAccountStatusPort.ChangeAdminUserAccountStatusResult(
                21L, AdminUserAccountStatus.ACTIVE));

    var result =
        service.execute(
            new ChangeAdminUserStatusCommand(9L, 21L, AdminUserAccountStatus.ACTIVE, "clear"));

    assertThat(result.userId()).isEqualTo(21L);
    assertThat(result.status()).isEqualTo(AdminUserAccountStatus.ACTIVE);
  }

  @Test
  @DisplayName("자기 자신 정지는 금지한다")
  void execute_selfBlock_throws() {
    assertThatThrownBy(
            () ->
                service.execute(
                    new ChangeAdminUserStatusCommand(9L, 9L, AdminUserAccountStatus.BLOCKED, null)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Cannot block own account");

    verify(loadAdminUserRolePort, never()).load(9L);
    verify(changeAdminUserAccountStatusPort, never())
        .change(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any());
  }

  @Test
  @DisplayName("ADMIN 계정은 변경 대상에서 제외한다")
  void execute_adminTarget_throws() {
    given(loadAdminUserRolePort.load(21L)).willReturn(AdminUserRole.ADMIN_GENERATED);

    assertThatThrownBy(
            () ->
                service.execute(
                    new ChangeAdminUserStatusCommand(
                        9L, 21L, AdminUserAccountStatus.BLOCKED, null)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Admin accounts are not managed");

    verify(changeAdminUserAccountStatusPort, never())
        .change(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any());
  }
}
