package momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.AutoCancelReservationUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.AutoSettleReservationUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

class MarketplaceReservationSchedulerConditionTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withBean(
              AutoCancelReservationUseCase.class, () -> mock(AutoCancelReservationUseCase.class))
          .withBean(
              AutoSettleReservationUseCase.class, () -> mock(AutoSettleReservationUseCase.class))
          .withBean(Clock.class, Clock::systemUTC)
          .withUserConfiguration(SchedulerConfig.class);

  @Test
  @DisplayName("auto-cancel scheduler 는 기본 활성화된다")
  void autoCancelSchedulerEnabledByDefault() {
    contextRunner.run(
        context -> assertThat(context).hasSingleBean(AutoCancelReservationScheduler.class));
  }

  @Test
  @DisplayName("auto-cancel scheduler 는 env 로 비활성화할 수 있다")
  void autoCancelSchedulerDisabledByProperty() {
    contextRunner
        .withPropertyValues("marketplace.reservation.auto-cancel.enabled=false")
        .run(context -> assertThat(context).doesNotHaveBean(AutoCancelReservationScheduler.class));
  }

  @Test
  @DisplayName("auto-settle scheduler 는 기본 비활성화된다")
  void autoSettleSchedulerDisabledByDefault() {
    contextRunner.run(
        context -> assertThat(context).doesNotHaveBean(AutoSettleReservationScheduler.class));
  }

  @Test
  @DisplayName("auto-settle scheduler 는 명시적으로 켠 경우에만 활성화된다")
  void autoSettleSchedulerEnabledByProperty() {
    contextRunner
        .withPropertyValues("marketplace.reservation.auto-settle.enabled=true")
        .run(context -> assertThat(context).hasSingleBean(AutoSettleReservationScheduler.class));
  }

  @Configuration
  @Import({AutoCancelReservationScheduler.class, AutoSettleReservationScheduler.class})
  static class SchedulerConfig {}
}
