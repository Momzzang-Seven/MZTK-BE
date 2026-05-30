package momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import momzzangseven.mztkbe.global.error.web3.Web3InvalidInputException;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceWeb3AutoSettleConfigurationValidationResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.ExecuteMarketplaceSchedulerAdminSettlementUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.ValidateMarketplaceWeb3AutoSettleConfigurationUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;

class MarketplaceWeb3AutoSettleSchedulerConfigurationValidatorTest {

  @Test
  @DisplayName("auto-settle가 꺼져 있으면 추가 검증 없이 통과한다")
  void validateConfiguration_allowsDisabledAutoSettle() {
    MarketplaceWeb3AutoSettleSchedulerProperties properties =
        new MarketplaceWeb3AutoSettleSchedulerProperties();
    properties.setEnabled(false);

    MarketplaceWeb3AutoSettleSchedulerConfigurationValidator validator =
        new MarketplaceWeb3AutoSettleSchedulerConfigurationValidator(
            properties, new MockEnvironment(), emptyProvider(), emptyProvider());

    assertThatCode(validator::validateConfiguration).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("auto-settle가 켜지면 marketplace admin enabled 플래그를 요구한다")
  void validateConfiguration_requiresMarketplaceAdminEnabled() {
    MarketplaceWeb3AutoSettleSchedulerProperties properties = enabledProperties();
    MockEnvironment environment =
        new MockEnvironment().withProperty("web3.execution.internal.enabled", "true");

    MarketplaceWeb3AutoSettleSchedulerConfigurationValidator validator =
        new MarketplaceWeb3AutoSettleSchedulerConfigurationValidator(
            properties, environment, emptyProvider(), emptyProvider());

    assertThatThrownBy(validator::validateConfiguration)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("web3.marketplace.admin.enabled=true");
  }

  @Test
  @DisplayName("batchSize와 scanSize의 교차 정책 위반은 startup에서 fail-fast 한다")
  void validateConfiguration_failsFastOnInvalidPolicyShape() {
    MarketplaceWeb3AutoSettleSchedulerProperties properties = enabledProperties();
    properties.setBatchSize(100);
    properties.setScanSize(99);
    Environment environment =
        new MockEnvironment()
            .withProperty("web3.marketplace.admin.enabled", "true")
            .withProperty("web3.execution.internal.enabled", "true");

    MarketplaceWeb3AutoSettleSchedulerConfigurationValidator validator =
        new MarketplaceWeb3AutoSettleSchedulerConfigurationValidator(
            properties, environment, emptyProvider(), emptyProvider());

    assertThatThrownBy(validator::validateConfiguration)
        .isInstanceOf(Web3InvalidInputException.class)
        .hasMessageContaining("scanSize must be between batchSize and 2000");
  }

  @Test
  @DisplayName("필수 validation use case bean 누락은 명시적인 메시지로 실패한다")
  void validateConfiguration_requiresValidationUseCaseBean() {
    MarketplaceWeb3AutoSettleSchedulerProperties properties = enabledProperties();
    Environment environment =
        new MockEnvironment()
            .withProperty("web3.marketplace.admin.enabled", "true")
            .withProperty("web3.execution.internal.enabled", "true");

    MarketplaceWeb3AutoSettleSchedulerConfigurationValidator validator =
        new MarketplaceWeb3AutoSettleSchedulerConfigurationValidator(
            properties, environment, emptyProvider(), emptyProvider());

    assertThatThrownBy(validator::validateConfiguration)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(
            "requires marketplace web3 auto-settle configuration validation use case");
  }

  @Test
  @DisplayName("flags와 bean graph가 준비되면 validation use case를 통과시킨다")
  void validateConfiguration_allowsValidValidationResult() {
    MarketplaceWeb3AutoSettleSchedulerProperties properties = enabledProperties();
    Environment environment =
        new MockEnvironment()
            .withProperty("web3.marketplace.admin.enabled", "true")
            .withProperty("web3.execution.internal.enabled", "true")
            .withProperty("web3.marketplace.admin.fail-fast", "true");
    ValidateMarketplaceWeb3AutoSettleConfigurationUseCase validateUseCase =
        MarketplaceWeb3AutoSettleConfigurationValidationResult::ok;

    MarketplaceWeb3AutoSettleSchedulerConfigurationValidator validator =
        new MarketplaceWeb3AutoSettleSchedulerConfigurationValidator(
            properties,
            environment,
            provider(validateUseCase),
            provider(mock(ExecuteMarketplaceSchedulerAdminSettlementUseCase.class)));

    assertThatCode(validator::validateConfiguration).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("fail-fast가 켜져 있으면 use case authority warning을 startup failure로 처리한다")
  void validateConfiguration_failsFastOnAuthorityWarning() {
    MarketplaceWeb3AutoSettleSchedulerProperties properties = enabledProperties();
    Environment environment =
        new MockEnvironment()
            .withProperty("web3.marketplace.admin.enabled", "true")
            .withProperty("web3.execution.internal.enabled", "true")
            .withProperty("web3.marketplace.admin.fail-fast", "true");
    ValidateMarketplaceWeb3AutoSettleConfigurationUseCase validateUseCase =
        () ->
            MarketplaceWeb3AutoSettleConfigurationValidationResult.authorityWarning(
                "Marketplace admin execution signer is unavailable at startup");

    MarketplaceWeb3AutoSettleSchedulerConfigurationValidator validator =
        new MarketplaceWeb3AutoSettleSchedulerConfigurationValidator(
            properties,
            environment,
            provider(validateUseCase),
            provider(mock(ExecuteMarketplaceSchedulerAdminSettlementUseCase.class)));

    assertThatThrownBy(validator::validateConfiguration)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("signer is unavailable");
  }

  @Test
  @DisplayName("fail-fast가 꺼져 있으면 use case authority warning을 경고로만 처리한다")
  void validateConfiguration_allowsAuthorityWarningWhenFailFastDisabled() {
    MarketplaceWeb3AutoSettleSchedulerProperties properties = enabledProperties();
    Environment environment =
        new MockEnvironment()
            .withProperty("web3.marketplace.admin.enabled", "true")
            .withProperty("web3.execution.internal.enabled", "true")
            .withProperty("web3.marketplace.admin.fail-fast", "false");
    ValidateMarketplaceWeb3AutoSettleConfigurationUseCase validateUseCase =
        () ->
            MarketplaceWeb3AutoSettleConfigurationValidationResult.authorityWarning(
                "Marketplace admin execution signer is unavailable at startup");

    MarketplaceWeb3AutoSettleSchedulerConfigurationValidator validator =
        new MarketplaceWeb3AutoSettleSchedulerConfigurationValidator(
            properties,
            environment,
            provider(validateUseCase),
            provider(mock(ExecuteMarketplaceSchedulerAdminSettlementUseCase.class)));

    assertThatCode(validator::validateConfiguration).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("auto-settle enabled + admin disabled 조합은 validator가 startup failure로 막는다")
  void validatorConditionFailsContradictoryFlagsAtStartup() {
    new ApplicationContextRunner()
        .withUserConfiguration(ValidatorContext.class)
        .withBean(Clock.class, Clock::systemUTC)
        .withBean(
            ValidateMarketplaceWeb3AutoSettleConfigurationUseCase.class,
            () -> MarketplaceWeb3AutoSettleConfigurationValidationResult::ok)
        .withBean(
            ExecuteMarketplaceSchedulerAdminSettlementUseCase.class,
            () -> mock(ExecuteMarketplaceSchedulerAdminSettlementUseCase.class))
        .withPropertyValues(
            "web3.marketplace.admin.auto-settle.enabled=true",
            "web3.execution.internal.enabled=true")
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context.getStartupFailure())
                  .hasRootCauseMessage(
                      "web3.marketplace.admin.auto-settle.enabled=true requires web3.marketplace.admin.enabled=true");
            });
  }

  private static MarketplaceWeb3AutoSettleSchedulerProperties enabledProperties() {
    MarketplaceWeb3AutoSettleSchedulerProperties properties =
        new MarketplaceWeb3AutoSettleSchedulerProperties();
    properties.setEnabled(true);
    return properties;
  }

  private static <T> ObjectProvider<T> provider(T value) {
    return new SingleObjectProvider<>(value);
  }

  private static <T> ObjectProvider<T> emptyProvider() {
    return new SingleObjectProvider<>(null);
  }

  private static final class SingleObjectProvider<T> implements ObjectProvider<T> {

    private final T value;

    private SingleObjectProvider(T value) {
      this.value = value;
    }

    @Override
    public T getObject(Object... args) {
      return value;
    }

    @Override
    public T getIfAvailable() {
      return value;
    }

    @Override
    public T getIfUnique() {
      return value;
    }

    @Override
    public T getObject() {
      return value;
    }
  }

  @Configuration
  @Import(MarketplaceWeb3AutoSettleSchedulerConfigurationValidator.class)
  static class ValidatorContext {

    @Bean
    MarketplaceWeb3AutoSettleSchedulerProperties marketplaceWeb3AutoSettleSchedulerProperties(
        Environment environment) {
      MarketplaceWeb3AutoSettleSchedulerProperties properties =
          new MarketplaceWeb3AutoSettleSchedulerProperties();
      properties.setEnabled(
          environment.getProperty(
              "web3.marketplace.admin.auto-settle.enabled", Boolean.class, false));
      return properties;
    }
  }
}
