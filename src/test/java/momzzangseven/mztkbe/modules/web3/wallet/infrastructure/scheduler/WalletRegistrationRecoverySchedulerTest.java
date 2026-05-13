package momzzangseven.mztkbe.modules.web3.wallet.infrastructure.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import momzzangseven.mztkbe.modules.web3.wallet.application.dto.RunWalletRegistrationRecoveryBatchResult;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.in.RunWalletRegistrationRecoveryBatchUseCase;
import momzzangseven.mztkbe.modules.web3.wallet.infrastructure.config.WalletRegistrationProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WalletRegistrationRecoverySchedulerTest {

  @Mock private RunWalletRegistrationRecoveryBatchUseCase recoveryBatchUseCase;

  private WalletRegistrationRecoveryScheduler scheduler;

  @BeforeEach
  void setUp() {
    WalletRegistrationProperties properties = new WalletRegistrationProperties();
    properties.getRecovery().setBatchSize(25);
    scheduler = new WalletRegistrationRecoveryScheduler(recoveryBatchUseCase, properties);
  }

  @Test
  void run_delegatesToRecoveryBatchUseCaseWithConfiguredBatchSize() {
    when(recoveryBatchUseCase.execute(any()))
        .thenReturn(new RunWalletRegistrationRecoveryBatchResult(0, 0, 0, 0));

    scheduler.run();

    verify(recoveryBatchUseCase).execute(argThat(command -> command.batchSize() == 25));
  }

  @Test
  void run_repeatsUntilBatchIsDrained() {
    when(recoveryBatchUseCase.execute(any()))
        .thenReturn(
            new RunWalletRegistrationRecoveryBatchResult(25, 20, 5, 0),
            new RunWalletRegistrationRecoveryBatchResult(10, 8, 2, 0));

    scheduler.run();

    verify(recoveryBatchUseCase, times(2)).execute(argThat(command -> command.batchSize() == 25));
  }

  @Test
  void run_stopsWhenFullBatchMakesNoProgress() {
    when(recoveryBatchUseCase.execute(any()))
        .thenReturn(new RunWalletRegistrationRecoveryBatchResult(25, 0, 25, 0));

    scheduler.run();

    verify(recoveryBatchUseCase).execute(argThat(command -> command.batchSize() == 25));
  }
}
