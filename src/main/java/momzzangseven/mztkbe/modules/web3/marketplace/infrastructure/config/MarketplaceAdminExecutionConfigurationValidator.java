package momzzangseven.mztkbe.modules.web3.marketplace.infrastructure.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.config.ConditionalOnMarketplaceAdminEnabled;
import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceAdminExecutionAuthorityStatus;
import momzzangseven.mztkbe.modules.web3.marketplace.application.dto.MarketplaceInternalExecutionPolicyStatus;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.in.LoadMarketplaceAdminExecutionAuthorityUseCase;
import momzzangseven.mztkbe.modules.web3.marketplace.application.port.out.LoadMarketplaceInternalExecutionPolicyPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
@ConditionalOnMarketplaceAdminEnabled
@ConditionalOnBean({
  LoadMarketplaceInternalExecutionPolicyPort.class,
  LoadMarketplaceAdminExecutionAuthorityUseCase.class
})
public class MarketplaceAdminExecutionConfigurationValidator {

  private static final String ENABLE_MARKETPLACE_ADMIN_SETTLE_MESSAGE =
      "Marketplace admin execution requires web3.execution.internal.action-policy to enable "
          + "MARKETPLACE_ADMIN_SETTLE";
  private static final String ENABLE_MARKETPLACE_ADMIN_REFUND_MESSAGE =
      "Marketplace admin execution requires web3.execution.internal.action-policy to enable "
          + "MARKETPLACE_ADMIN_REFUND";
  private static final String SIGNER_UNAVAILABLE_MESSAGE =
      "Marketplace admin execution signer is unavailable at startup";
  private static final String RELAYER_CHECK_FAILED_MESSAGE =
      "Marketplace admin execution failed to validate current server signer relayer "
          + "registration at startup";
  private static final String RELAYER_NOT_REGISTERED_MESSAGE =
      "Marketplace admin execution signer is not registered as relayer at startup: "
          + "signerAddress=%s";

  private final LoadMarketplaceInternalExecutionPolicyPort
      loadMarketplaceInternalExecutionPolicyPort;
  private final LoadMarketplaceAdminExecutionAuthorityUseCase
      loadMarketplaceAdminExecutionAuthorityUseCase;

  @Value("${web3.marketplace.admin.fail-fast:false}")
  private boolean failFast;

  @PostConstruct
  void validateOnStartup() {
    validateConfiguration();
  }

  void validateConfiguration() {
    MarketplaceInternalExecutionPolicyStatus policy =
        loadMarketplaceInternalExecutionPolicyPort.load();
    if (!policy.enabled()) {
      throw new IllegalStateException(
          "Marketplace admin execution requires web3.execution.internal.enabled=true");
    }
    if (!policy.marketplaceAdminSettleEnabled()) {
      throw new IllegalStateException(ENABLE_MARKETPLACE_ADMIN_SETTLE_MESSAGE);
    }
    if (!policy.marketplaceAdminRefundEnabled()) {
      throw new IllegalStateException(ENABLE_MARKETPLACE_ADMIN_REFUND_MESSAGE);
    }

    MarketplaceAdminExecutionAuthorityStatus authority =
        loadMarketplaceAdminExecutionAuthorityUseCase.execute();
    if (!authority.serverSignerAvailable() || authority.serverSignerAddress() == null) {
      handleMisconfiguration(SIGNER_UNAVAILABLE_MESSAGE, null);
      return;
    }

    if (MarketplaceAdminExecutionAuthorityStatus.RELAYER_REGISTRATION_CHECK_FAILED.equals(
        authority.relayerRegistrationStatus())) {
      handleMisconfiguration(RELAYER_CHECK_FAILED_MESSAGE, null);
      return;
    }
    if (!authority.relayerRegistered()) {
      handleMisconfiguration(
          RELAYER_NOT_REGISTERED_MESSAGE.formatted(authority.serverSignerAddress()), null);
    }
  }

  private void handleMisconfiguration(String message, RuntimeException cause) {
    if (failFast) {
      if (cause == null) {
        throw new IllegalStateException(message);
      }
      throw new IllegalStateException(message, cause);
    }
    if (cause == null) {
      log.warn(message);
    } else {
      log.warn(message, cause);
    }
  }
}
