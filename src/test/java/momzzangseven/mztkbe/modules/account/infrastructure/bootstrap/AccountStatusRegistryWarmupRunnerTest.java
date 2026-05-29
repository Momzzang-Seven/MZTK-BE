package momzzangseven.mztkbe.modules.account.infrastructure.bootstrap;

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
@DisplayName("AccountStatusRegistryWarmupRunner 단위 테스트")
class AccountStatusRegistryWarmupRunnerTest {

  @Mock private ReconcileAccountStatusRegistryUseCase reconcileUseCase;

  @InjectMocks private AccountStatusRegistryWarmupRunner runner;

  @Test
  @DisplayName("run() - 기동 시 reconcile UseCase 에 정확히 1회 위임")
  void run_delegatesToReconcileOnce() {
    runner.run(null);

    verify(reconcileUseCase, times(1)).reconcile();
  }
}
