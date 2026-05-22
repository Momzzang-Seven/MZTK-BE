package momzzangseven.mztkbe.modules.web3.marketplace.infrastructure.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.global.config.ConditionalOnMarketplaceAdminEnabled;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.InternalExecutionIssuerPolicyView;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.GetInternalExecutionIssuerPolicyUseCase;
import momzzangseven.mztkbe.modules.web3.marketplace.infrastructure.external.web3.MarketplaceContractCallSupport;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.ExecutionSignerCapabilityView;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.in.ProbeTreasuryWalletCapabilityUseCase;
import momzzangseven.mztkbe.modules.web3.treasury.domain.vo.TreasuryRole;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
@ConditionalOnMarketplaceAdminEnabled
@ConditionalOnBean(ProbeTreasuryWalletCapabilityUseCase.class)
public class MarketplaceAdminExecutionConfigurationValidator {

  private static final String ENABLE_MARKETPLACE_ADMIN_SETTLE_MESSAGE =
      "Marketplace admin execution requires web3.execution.internal.action-policy to enable "
          + "MARKETPLACE_ADMIN_SETTLE";
  private static final String ENABLE_MARKETPLACE_ADMIN_REFUND_MESSAGE =
      "Marketplace admin execution requires web3.execution.internal.action-policy to enable "
          + "MARKETPLACE_ADMIN_REFUND";
  private static final String SIGNER_UNAVAILABLE_MESSAGE =
      "Marketplace admin execution signer is unavailable at startup: walletAlias=%s, "
          + "slotStatus=%s, failureReason=%s";
  private static final String RELAYER_CHECK_FAILED_MESSAGE =
      "Marketplace admin execution failed to validate current server signer relayer "
          + "registration at startup";
  private static final String RELAYER_NOT_REGISTERED_MESSAGE =
      "Marketplace admin execution signer is not registered as relayer at startup: "
          + "signerAddress=%s";

  private final GetInternalExecutionIssuerPolicyUseCase getInternalExecutionIssuerPolicyUseCase;
  private final ProbeTreasuryWalletCapabilityUseCase probeTreasuryWalletCapabilityUseCase;
  private final MarketplaceContractCallSupport marketplaceContractCallSupport;
  private final MarketplaceEscrowProperties marketplaceEscrowProperties;

  @Value("${web3.marketplace.admin.fail-fast:false}")
  private boolean failFast;

  @PostConstruct
  void validateOnStartup() {
    validateConfiguration();
  }

  void validateConfiguration() {
    InternalExecutionIssuerPolicyView policy = getInternalExecutionIssuerPolicyUseCase.getPolicy();
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

    ExecutionSignerCapabilityView signer =
        probeTreasuryWalletCapabilityUseCase.probe(TreasuryRole.MARKETPLACE_SIGNER.toAlias());
    if (!signer.signable() || signer.signerAddress() == null) {
      handleMisconfiguration(
          SIGNER_UNAVAILABLE_MESSAGE.formatted(
              signer.walletAlias(), signer.slotStatus(), signer.failureReason()),
          null);
      return;
    }

    boolean relayerRegistered;
    try {
      relayerRegistered =
          marketplaceContractCallSupport.isRelayerRegistered(
              marketplaceEscrowProperties.getMarketplaceContractAddress(), signer.signerAddress());
    } catch (RuntimeException ex) {
      handleMisconfiguration(RELAYER_CHECK_FAILED_MESSAGE, ex);
      return;
    }
    if (!relayerRegistered) {
      handleMisconfiguration(
          RELAYER_NOT_REGISTERED_MESSAGE.formatted(signer.signerAddress()), null);
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
