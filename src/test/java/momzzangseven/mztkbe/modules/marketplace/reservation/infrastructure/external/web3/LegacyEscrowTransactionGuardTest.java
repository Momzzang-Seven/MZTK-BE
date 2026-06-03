package momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.external.web3;

import static org.assertj.core.api.Assertions.assertThat;

import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SubmitEscrowTransactionPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@DisplayName("LegacyEscrowTransactionConfigurationValidator")
class LegacyEscrowTransactionGuardTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withPropertyValues("web3.eip7702.enabled=true")
          .withUserConfiguration(LegacyEscrowConfig.class);

  @Test
  @DisplayName("EIP-7702 enabled + legacy auto-cancel enabled 이면 disabled direct TX 경고만 남긴다")
  void allowsEnabledLegacyAutoCancelWithDisabledDirectTransactionPortByDefault() {
    contextRunner
        .withPropertyValues("marketplace.reservation.auto-cancel.enabled=true")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).hasSingleBean(SubmitEscrowTransactionPort.class);
            });
  }

  @Test
  @DisplayName("legacy direct escrow fail-fast=true 이면 disabled direct TX 를 startup 에서 차단한다")
  void rejectsEnabledLegacyAutoCancelWithDisabledDirectTransactionPortWhenFailFastOn() {
    contextRunner
        .withPropertyValues(
            "marketplace.reservation.auto-cancel.enabled=true",
            "marketplace.reservation.legacy-direct-escrow.fail-fast=true")
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context.getStartupFailure())
                  .hasRootCauseMessage(
                      "Legacy marketplace reservation direct escrow transaction is disabled, but "
                          + "marketplace.reservation.auto-cancel.enabled is enabled. Disable the "
                          + "legacy scheduler or migrate it to marketplace admin execution "
                          + "intents.");
            });
  }

  @Test
  @DisplayName("legacy scheduler 가 모두 꺼져 있으면 disabled direct TX fallback 은 startup 을 막지 않는다")
  void allowsDisabledDirectTransactionPortWhenLegacySchedulersAreOff() {
    contextRunner
        .withPropertyValues(
            "marketplace.reservation.auto-cancel.enabled=false",
            "marketplace.reservation.auto-settle.enabled=false")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).hasSingleBean(SubmitEscrowTransactionPort.class);
            });
  }

  @Configuration
  @Import({
    LegacyEscrowTransactionDisabledConfig.class,
    LegacyEscrowTransactionConfigurationValidator.class
  })
  static class LegacyEscrowConfig {}
}
