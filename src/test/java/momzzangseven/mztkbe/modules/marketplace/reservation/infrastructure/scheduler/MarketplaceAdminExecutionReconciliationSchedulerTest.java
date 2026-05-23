package momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.ReconcileMarketplaceAdminTerminalExecutionAttemptResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.ReconcileMarketplaceAdminTerminalExecutionAttemptUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class MarketplaceAdminExecutionReconciliationSchedulerTest {

  @Test
  @DisplayName("admin enabled 상태에서는 reconciliation scheduler bean을 기본 등록한다")
  void registersByDefaultWhenAdminEnabled() {
    contextRunner()
        .withPropertyValues(
            "web3.execution.internal.enabled=true", "web3.marketplace.admin.enabled=true")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context)
                  .hasSingleBean(MarketplaceAdminExecutionReconciliationScheduler.class);
            });
  }

  @Test
  @DisplayName("reconciliation disabled 상태에서는 scheduler bean을 등록하지 않는다")
  void doesNotRegisterWhenReconciliationDisabled() {
    contextRunner()
        .withPropertyValues(
            "web3.execution.internal.enabled=true",
            "web3.marketplace.admin.enabled=true",
            "web3.marketplace.admin.reconciliation.enabled=false")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context)
                  .doesNotHaveBean(MarketplaceAdminExecutionReconciliationScheduler.class);
            });
  }

  @Test
  @DisplayName("configured batch size로 confirmed/terminal reconciliation use case를 호출한다")
  void runDelegatesToReconciliationUseCaseWithConfiguredBatchSize() {
    ReconcileMarketplaceAdminTerminalExecutionAttemptUseCase useCase =
        mock(ReconcileMarketplaceAdminTerminalExecutionAttemptUseCase.class);
    when(useCase.execute(org.mockito.ArgumentMatchers.any()))
        .thenReturn(new ReconcileMarketplaceAdminTerminalExecutionAttemptResult(1, 1, 0, 0));
    MarketplaceAdminExecutionReconciliationScheduler scheduler =
        new MarketplaceAdminExecutionReconciliationScheduler(useCase, 25, 20);

    scheduler.run();

    verify(useCase).execute(argThat(command -> command.batchSize() == 25));
  }

  @Test
  @DisplayName("full batch가 계속 처리되면 reconciliation 후보가 drain될 때까지 반복한다")
  void runRepeatsUntilBatchIsDrained() {
    ReconcileMarketplaceAdminTerminalExecutionAttemptUseCase useCase =
        mock(ReconcileMarketplaceAdminTerminalExecutionAttemptUseCase.class);
    when(useCase.execute(org.mockito.ArgumentMatchers.any()))
        .thenReturn(
            new ReconcileMarketplaceAdminTerminalExecutionAttemptResult(25, 20, 5, 0),
            new ReconcileMarketplaceAdminTerminalExecutionAttemptResult(3, 3, 0, 0));
    MarketplaceAdminExecutionReconciliationScheduler scheduler =
        new MarketplaceAdminExecutionReconciliationScheduler(useCase, 25, 20);

    scheduler.run();

    verify(useCase, times(2)).execute(argThat(command -> command.batchSize() == 25));
  }

  @Test
  @DisplayName("full batch가 계속 나오면 configured max batches에서 멈춘다")
  void runStopsAtConfiguredMaxBatches() {
    ReconcileMarketplaceAdminTerminalExecutionAttemptUseCase useCase =
        mock(ReconcileMarketplaceAdminTerminalExecutionAttemptUseCase.class);
    when(useCase.execute(org.mockito.ArgumentMatchers.any()))
        .thenReturn(new ReconcileMarketplaceAdminTerminalExecutionAttemptResult(25, 25, 0, 0));
    MarketplaceAdminExecutionReconciliationScheduler scheduler =
        new MarketplaceAdminExecutionReconciliationScheduler(useCase, 25, 3);

    scheduler.run();

    verify(useCase, times(3)).execute(argThat(command -> command.batchSize() == 25));
  }

  private ApplicationContextRunner contextRunner() {
    return new ApplicationContextRunner()
        .withUserConfiguration(MarketplaceAdminExecutionReconciliationScheduler.class)
        .withBean(
            ReconcileMarketplaceAdminTerminalExecutionAttemptUseCase.class,
            () -> mock(ReconcileMarketplaceAdminTerminalExecutionAttemptUseCase.class))
        .withPropertyValues(
            "web3.marketplace.admin.reconciliation.batch-size=25",
            "web3.marketplace.admin.reconciliation.max-batches-per-run=20");
  }
}
