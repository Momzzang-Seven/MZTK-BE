package momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import momzzangseven.mztkbe.global.config.ConditionalOnMarketplaceAdminEnabled;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.RunMarketplaceWeb3AutoSettleBatchUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.config.MarketplaceWeb3AutoSettleSchedulerProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class MarketplaceWeb3AutoSettleSchedulerConditionTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withBean(
              RunMarketplaceWeb3AutoSettleBatchUseCase.class,
              () -> mock(RunMarketplaceWeb3AutoSettleBatchUseCase.class))
          .withBean(Clock.class, Clock::systemUTC)
          .withUserConfiguration(SchedulerConfig.class);

  @Test
  @DisplayName(
      "internal execution, marketplace admin, auto-settle 플래그가 모두 켜지면 scheduler bean을 등록한다")
  void registersWhenInternalMarketplaceAdminAndAutoSettleAreEnabled() {
    contextRunner
        .withPropertyValues(
            "web3.execution.internal.enabled=true",
            "web3.marketplace.admin.enabled=true",
            "web3.marketplace.admin.auto-settle.enabled=true")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).hasSingleBean(MarketplaceWeb3AutoSettleScheduler.class);
            });
  }

  @Test
  @DisplayName(
      "ConditionalOnMarketplaceAdminEnabled에 따라 internal execution 플래그가 꺼져 있으면 bean을 등록하지 않는다")
  void doesNotRegisterWhenInternalExecutionDisabled() {
    contextRunner
        .withPropertyValues(
            "web3.execution.internal.enabled=false",
            "web3.marketplace.admin.enabled=true",
            "web3.marketplace.admin.auto-settle.enabled=true")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).doesNotHaveBean(MarketplaceWeb3AutoSettleScheduler.class);
            });
  }

  @Test
  @DisplayName("legacy auto-settle 플래그만으로는 web3 scheduler bean을 등록하지 않는다")
  void doesNotRegisterFromLegacyAutoSettleFlag() {
    contextRunner
        .withPropertyValues("marketplace.reservation.auto-settle.enabled=true")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).doesNotHaveBean(MarketplaceWeb3AutoSettleScheduler.class);
            });
  }

  @Test
  @DisplayName("auto-settle 플래그가 꺼져 있으면 marketplace admin enabled여도 bean을 등록하지 않는다")
  void doesNotRegisterWhenAutoSettleDisabled() {
    contextRunner
        .withPropertyValues(
            "web3.marketplace.admin.enabled=true",
            "web3.marketplace.admin.auto-settle.enabled=false")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).doesNotHaveBean(MarketplaceWeb3AutoSettleScheduler.class);
            });
  }

  @Configuration
  static class SchedulerConfig {

    @Bean
    @ConditionalOnMarketplaceAdminEnabled
    @ConditionalOnProperty(
        prefix = "web3.marketplace.admin.auto-settle",
        name = "enabled",
        havingValue = "true")
    MarketplaceWeb3AutoSettleScheduler marketplaceWeb3AutoSettleScheduler(
        RunMarketplaceWeb3AutoSettleBatchUseCase runBatchUseCase,
        MarketplaceWeb3AutoSettleSchedulerProperties properties,
        Clock clock) {
      return new MarketplaceWeb3AutoSettleScheduler(runBatchUseCase, properties, clock);
    }

    @Bean
    MarketplaceWeb3AutoSettleSchedulerProperties marketplaceWeb3AutoSettleSchedulerProperties() {
      return new MarketplaceWeb3AutoSettleSchedulerProperties();
    }
  }
}
