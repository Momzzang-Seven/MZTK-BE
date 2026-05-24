package momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.RecoverExpiredMarketplaceAdminExecutionAttemptResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.RecoverExpiredMarketplaceAdminExecutionAttemptUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class MarketplaceAdminExecutionAttemptMaintenanceSchedulerTest {

  private final Clock clock = Clock.fixed(Instant.parse("2026-05-21T00:00:00Z"), ZoneOffset.UTC);

  @Test
  @DisplayName("admin/recovery enabled 상태에서만 maintenance scheduler bean을 등록한다")
  void registersOnlyWhenAdminRecoveryEnabled() {
    contextRunner()
        .withPropertyValues(
            "web3.execution.internal.enabled=true",
            "web3.marketplace.admin.enabled=true",
            "web3.marketplace.admin.recovery.enabled=true")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context)
                  .hasSingleBean(MarketplaceAdminExecutionAttemptMaintenanceScheduler.class);
            });
  }

  @Test
  @DisplayName("recovery disabled 상태에서는 maintenance scheduler bean을 등록하지 않는다")
  void doesNotRegisterWhenRecoveryDisabled() {
    contextRunner()
        .withPropertyValues(
            "web3.execution.internal.enabled=true",
            "web3.marketplace.admin.enabled=true",
            "web3.marketplace.admin.recovery.enabled=false")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context)
                  .doesNotHaveBean(MarketplaceAdminExecutionAttemptMaintenanceScheduler.class);
            });
  }

  @Test
  @DisplayName("configured batch size로 expired unbound admin attempt recovery use case만 호출한다")
  void runDelegatesToRecoveryUseCaseWithConfiguredBatchSize() {
    RecoverExpiredMarketplaceAdminExecutionAttemptUseCase useCase =
        mock(RecoverExpiredMarketplaceAdminExecutionAttemptUseCase.class);
    when(useCase.execute(any()))
        .thenReturn(new RecoverExpiredMarketplaceAdminExecutionAttemptResult(10, 7, 3, 0));
    MarketplaceAdminExecutionAttemptMaintenanceScheduler scheduler =
        new MarketplaceAdminExecutionAttemptMaintenanceScheduler(useCase, clock, 25, 20);

    scheduler.run();

    verify(useCase).execute(argThat(command -> command.batchSize() == 25));
  }

  @Test
  @DisplayName("full batch가 계속 처리되면 drain될 때까지 반복한다")
  void runRepeatsUntilBatchIsDrained() {
    RecoverExpiredMarketplaceAdminExecutionAttemptUseCase useCase =
        mock(RecoverExpiredMarketplaceAdminExecutionAttemptUseCase.class);
    when(useCase.execute(any()))
        .thenReturn(
            new RecoverExpiredMarketplaceAdminExecutionAttemptResult(25, 20, 5, 0),
            new RecoverExpiredMarketplaceAdminExecutionAttemptResult(2, 2, 0, 0));
    MarketplaceAdminExecutionAttemptMaintenanceScheduler scheduler =
        new MarketplaceAdminExecutionAttemptMaintenanceScheduler(useCase, clock, 25, 20);

    scheduler.run();

    verify(useCase, times(2)).execute(argThat(command -> command.batchSize() == 25));
  }

  @Test
  @DisplayName("full batch가 계속 처리되면 maxBatchesPerRun에서 멈춘다")
  void runStopsAtMaxBatchesPerRun() {
    RecoverExpiredMarketplaceAdminExecutionAttemptUseCase useCase =
        mock(RecoverExpiredMarketplaceAdminExecutionAttemptUseCase.class);
    when(useCase.execute(any()))
        .thenReturn(new RecoverExpiredMarketplaceAdminExecutionAttemptResult(25, 25, 0, 0));
    MarketplaceAdminExecutionAttemptMaintenanceScheduler scheduler =
        new MarketplaceAdminExecutionAttemptMaintenanceScheduler(useCase, clock, 25, 3);

    scheduler.run();

    verify(useCase, times(3)).execute(argThat(command -> command.batchSize() == 25));
  }

  private ApplicationContextRunner contextRunner() {
    return new ApplicationContextRunner()
        .withUserConfiguration(MarketplaceAdminExecutionAttemptMaintenanceScheduler.class)
        .withBean(
            RecoverExpiredMarketplaceAdminExecutionAttemptUseCase.class,
            () -> mock(RecoverExpiredMarketplaceAdminExecutionAttemptUseCase.class))
        .withBean(Clock.class, () -> clock)
        .withPropertyValues(
            "web3.marketplace.admin.recovery.batch-size=25",
            "web3.marketplace.admin.recovery.max-batches-per-run=20");
  }
}
