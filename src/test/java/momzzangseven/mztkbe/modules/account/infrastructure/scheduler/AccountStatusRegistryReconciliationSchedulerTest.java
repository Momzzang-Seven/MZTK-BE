package momzzangseven.mztkbe.modules.account.infrastructure.scheduler;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import momzzangseven.mztkbe.modules.account.application.port.in.ReconcileAccountStatusRegistryUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("AccountStatusRegistryReconciliationScheduler 단위 테스트")
class AccountStatusRegistryReconciliationSchedulerTest {

  @Mock private ReconcileAccountStatusRegistryUseCase reconcileUseCase;

  @InjectMocks private AccountStatusRegistryReconciliationScheduler scheduler;

  @Test
  @DisplayName("run() - reconcile UseCase 에 정확히 1회 위임")
  void run_delegatesToReconcileOnce() {
    scheduler.run();

    verify(reconcileUseCase, times(1)).reconcile();
  }
}
