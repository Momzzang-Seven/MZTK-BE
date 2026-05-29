package momzzangseven.mztkbe.modules.account.application.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Map;
import momzzangseven.mztkbe.modules.account.application.port.out.LoadNonActiveUserStatusesPort;
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
@DisplayName("ReconcileAccountStatusRegistryService 단위 테스트")
class ReconcileAccountStatusRegistryServiceTest {

  @Mock private LoadNonActiveUserStatusesPort loadPort;
  @Mock private UpdateAccountStatusRegistryPort updatePort;

  @InjectMocks private ReconcileAccountStatusRegistryService service;

  @Test
  @DisplayName("DB 스냅샷을 읽어 denylist 를 통째로 교체한다")
  void reconcile_happyPath_replacesAll() {
    Map<Long, AccountStatus> snapshot =
        Map.of(1L, AccountStatus.BLOCKED, 2L, AccountStatus.DELETED);
    given(loadPort.loadAllNonActive()).willReturn(snapshot);

    service.reconcile();

    verify(updatePort).replaceAll(snapshot);
  }

  @Test
  @DisplayName("DB 조회가 실패하면 예외를 전파하지 않고 denylist 를 그대로 둔다")
  void reconcile_loadFails_doesNotThrowAndLeavesDenylist() {
    given(loadPort.loadAllNonActive()).willThrow(new RuntimeException("db down"));

    assertThatCode(() -> service.reconcile()).doesNotThrowAnyException();

    verify(updatePort, never()).replaceAll(ArgumentMatchers.anyMap());
  }
}
