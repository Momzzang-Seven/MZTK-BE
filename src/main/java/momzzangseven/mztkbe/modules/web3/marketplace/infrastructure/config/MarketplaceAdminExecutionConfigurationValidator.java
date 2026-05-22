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
      throw new IllegalStateException(
          "Marketplace admin execution requires web3.execution.internal.action-policy to enable MARKETPLACE_ADMIN_SETTLE");
    }
    if (!policy.marketplaceAdminRefundEnabled()) {
      throw new IllegalStateException(
          "Marketplace admin execution requires web3.execution.internal.action-policy to enable MARKETPLACE_ADMIN_REFUND");
    }

    ExecutionSignerCapabilityView signer =
        probeTreasuryWalletCapabilityUseCase.probe(TreasuryRole.MARKETPLACE_SIGNER.toAlias());
    if (!signer.signable() || signer.signerAddress() == null) {
      handleMisconfiguration(
          "Marketplace admin execution signer is unavailable at startup: walletAlias=%s, slotStatus=%s, failureReason=%s"
              .formatted(signer.walletAlias(), signer.slotStatus(), signer.failureReason()),
          null);
      return;
    }

    boolean relayerRegistered;
    try {
      relayerRegistered =
          marketplaceContractCallSupport.isRelayerRegistered(
              marketplaceEscrowProperties.getMarketplaceContractAddress(), signer.signerAddress());
    } catch (RuntimeException ex) {
      handleMisconfiguration(
          "Marketplace admin execution failed to validate current server signer relayer registration at startup",
          ex);
      return;
    }
    if (!relayerRegistered) {
      handleMisconfiguration(
          "Marketplace admin execution signer is not registered as relayer at startup: signerAddress=%s"
              .formatted(signer.signerAddress()),
          null);
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
