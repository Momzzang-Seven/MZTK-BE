package momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.dto.MarketplaceWeb3AutoSettleConfigurationValidationResult;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.ExecuteMarketplaceSchedulerAdminSettlementUseCase;
import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.in.ValidateMarketplaceWeb3AutoSettleConfigurationUseCase;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "web3.marketplace.admin.auto-settle",
    name = "enabled",
    havingValue = "true")
public class MarketplaceWeb3AutoSettleSchedulerConfigurationValidator {

  private final MarketplaceWeb3AutoSettleSchedulerProperties properties;
  private final Environment environment;
  private final ObjectProvider<ValidateMarketplaceWeb3AutoSettleConfigurationUseCase>
      validateUseCaseProvider;
  private final ObjectProvider<ExecuteMarketplaceSchedulerAdminSettlementUseCase>
      executeUseCaseProvider;

  @PostConstruct
  void validateOnStartup() {
    validateConfiguration();
  }

  void validateConfiguration() {
    if (!Boolean.TRUE.equals(properties.getEnabled())) {
      return;
    }
    properties.loadPolicy();
    requireFlag(
        "web3.marketplace.admin.enabled",
        "web3.marketplace.admin.auto-settle.enabled=true requires web3.marketplace.admin.enabled=true");
    requireFlag(
        "web3.execution.internal.enabled",
        "web3.marketplace.admin.auto-settle.enabled=true requires web3.execution.internal.enabled=true");

    ValidateMarketplaceWeb3AutoSettleConfigurationUseCase validateUseCase =
        requireBean(
            validateUseCaseProvider.getIfAvailable(),
            "web3.marketplace.admin.auto-settle.enabled=true requires marketplace web3 auto-settle configuration validation use case");
    requireBean(
        executeUseCaseProvider.getIfAvailable(),
        "web3.marketplace.admin.auto-settle.enabled=true requires scheduler admin settlement use case");

    MarketplaceWeb3AutoSettleConfigurationValidationResult validationResult =
        validateUseCase.validate();
    if (validationResult.hasAuthorityWarning()) {
      handleAuthorityMisconfiguration(validationResult.authorityMisconfigurationMessage());
    }
  }

  private void requireFlag(String key, String message) {
    if (!environment.getProperty(key, Boolean.class, false)) {
      throw new IllegalStateException(message);
    }
  }

  private <T> T requireBean(T bean, String message) {
    if (bean == null) {
      throw new IllegalStateException(message);
    }
    return bean;
  }

  private void handleAuthorityMisconfiguration(String message) {
    if (environment.getProperty("web3.marketplace.admin.fail-fast", Boolean.class, false)) {
      throw new IllegalStateException(message);
    }
    log.warn(message);
  }
}
