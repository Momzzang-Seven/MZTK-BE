package momzzangseven.mztkbe.modules.account.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.verify;

import java.util.Map;
import java.util.function.Supplier;
import momzzangseven.mztkbe.modules.account.application.port.out.LoadNonActiveUserStatusesPort;
import momzzangseven.mztkbe.modules.account.application.port.out.UpdateAccountStatusRegistryPort;
import momzzangseven.mztkbe.modules.account.domain.vo.AccountStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReconcileAccountStatusRegistryService 단위 테스트")
class ReconcileAccountStatusRegistryServiceTest {

  @Mock private LoadNonActiveUserStatusesPort loadPort;
  @Mock private UpdateAccountStatusRegistryPort updatePort;

  @Captor private ArgumentCaptor<Supplier<Map<Long, AccountStatus>>> loaderCaptor;

  @InjectMocks private ReconcileAccountStatusRegistryService service;

  @Test
  @DisplayName("replaceAll 에 넘긴 loader 가 호출되면 loadAllNonActive 의 결과를 반환한다")
  void reconcile_passesLoaderThatDelegatesToLoadPort() {
    Map<Long, AccountStatus> snapshot =
        Map.of(1L, AccountStatus.BLOCKED, 2L, AccountStatus.DELETED);
    given(loadPort.loadAllNonActive()).willReturn(snapshot);

    service.reconcile();

    // The service must pass the DB read as a supplier (so it runs under the registry lock), not an
    // eagerly-loaded snapshot. Capture the supplier and prove it delegates to loadAllNonActive().
    verify(updatePort).replaceAll(loaderCaptor.capture());
    assertThat(loaderCaptor.getValue().get()).isEqualTo(snapshot);
    verify(loadPort).loadAllNonActive();
  }

  @Test
  @DisplayName("replaceAll 이 던지면(=lock 내부 loader 실패가 전파됨) 서비스가 삼킨다(fail-soft)")
  void reconcile_replaceAllThrows_doesNotPropagate() {
    // In production the loader runs INSIDE replaceAll (under the registry lock); if the DB read
    // fails the registry rethrows. Model that by having the mock replaceAll throw. The service must
    // swallow it so the scheduler thread never dies.
    willAnswer(
            invocation -> {
              throw new RuntimeException("db down");
            })
        .given(updatePort)
        .replaceAll(any());

    assertThatCode(() -> service.reconcile()).doesNotThrowAnyException();
  }
}
