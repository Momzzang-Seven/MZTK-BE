package momzzangseven.mztkbe.modules.account.application.service;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import momzzangseven.mztkbe.modules.account.application.dto.ApplyAccountStatusChangeCommand;
import momzzangseven.mztkbe.modules.account.application.port.out.UpdateAccountStatusRegistryPort;
import momzzangseven.mztkbe.modules.account.domain.vo.AccountStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ApplyAccountStatusChangeService 단위 테스트")
class ApplyAccountStatusChangeServiceTest {

  @Mock private UpdateAccountStatusRegistryPort updatePort;

  @InjectMocks private ApplyAccountStatusChangeService service;

  @Test
  @DisplayName("BLOCKED 이면 denylist 에 put 하고 evict 는 호출하지 않는다")
  void execute_blocked_puts() {
    service.execute(new ApplyAccountStatusChangeCommand(21L, AccountStatus.BLOCKED));

    verify(updatePort).put(21L, AccountStatus.BLOCKED);
    verify(updatePort, never()).evict(ArgumentMatchers.anyLong());
  }

  @Test
  @DisplayName("DELETED 이면 denylist 에 put 한다")
  void execute_deleted_puts() {
    service.execute(new ApplyAccountStatusChangeCommand(21L, AccountStatus.DELETED));

    verify(updatePort).put(21L, AccountStatus.DELETED);
    verify(updatePort, never()).evict(ArgumentMatchers.anyLong());
  }

  @Test
  @DisplayName("UNVERIFIED 이면 denylist 에 put 한다")
  void execute_unverified_puts() {
    service.execute(new ApplyAccountStatusChangeCommand(21L, AccountStatus.UNVERIFIED));

    verify(updatePort).put(21L, AccountStatus.UNVERIFIED);
    verify(updatePort, never()).evict(ArgumentMatchers.anyLong());
  }

  @Test
  @DisplayName("ACTIVE 이면 denylist 에서 evict 하고 put 은 호출하지 않는다")
  void execute_active_evicts() {
    service.execute(new ApplyAccountStatusChangeCommand(21L, AccountStatus.ACTIVE));

    verify(updatePort).evict(21L);
    verify(updatePort, never()).put(ArgumentMatchers.anyLong(), ArgumentMatchers.any());
  }

  @Test
  @DisplayName("status 가 null 이면 (hard delete) denylist 에서 evict 한다")
  void execute_null_evicts() {
    service.execute(new ApplyAccountStatusChangeCommand(21L, null));

    verify(updatePort).evict(21L);
    verify(updatePort, never()).put(ArgumentMatchers.anyLong(), ArgumentMatchers.any());
    verifyNoMoreInteractions(updatePort);
  }
}
