package momzzangseven.mztkbe.modules.web3.wallet.infrastructure.config;

import java.math.BigDecimal;
import java.util.Optional;
import momzzangseven.mztkbe.global.error.wallet.WalletApprovalUnavailableException;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletApprovalCapability;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletApprovalExecutionStateView;
import momzzangseven.mztkbe.modules.web3.wallet.application.dto.WalletApprovalSponsorPolicy;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.BuildWalletApprovalExecutionDraftPort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.CancelWalletApprovalExecutionPort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LoadWalletApprovalCapabilityPort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LoadWalletApprovalExecutionStatePort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LoadWalletApprovalExecutionSupportPort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LoadWalletApprovalSponsorPolicyPort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.SubmitWalletApprovalExecutionDraftPort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.SyncWalletApprovalExecutionSuccessPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WalletApprovalExecutionConfig {

  private static final String DISABLED_REASON = "wallet approval execution is unavailable";

  @Bean
  @ConditionalOnMissingBean(BuildWalletApprovalExecutionDraftPort.class)
  BuildWalletApprovalExecutionDraftPort fallbackBuildWalletApprovalExecutionDraftPort() {
    return request -> {
      throw unavailable();
    };
  }

  @Bean
  @ConditionalOnMissingBean(SubmitWalletApprovalExecutionDraftPort.class)
  SubmitWalletApprovalExecutionDraftPort fallbackSubmitWalletApprovalExecutionDraftPort() {
    return draft -> {
      throw unavailable();
    };
  }

  @Bean
  @ConditionalOnMissingBean(LoadWalletApprovalCapabilityPort.class)
  LoadWalletApprovalCapabilityPort fallbackLoadWalletApprovalCapabilityPort() {
    return () -> WalletApprovalCapability.unavailable(DISABLED_REASON);
  }

  @Bean
  @ConditionalOnMissingBean(LoadWalletApprovalSponsorPolicyPort.class)
  LoadWalletApprovalSponsorPolicyPort fallbackLoadWalletApprovalSponsorPolicyPort() {
    return () ->
        new WalletApprovalSponsorPolicy(false, 21_000L, 1L, BigDecimal.ZERO, BigDecimal.ZERO);
  }

  @Bean
  @ConditionalOnMissingBean(LoadWalletApprovalExecutionSupportPort.class)
  LoadWalletApprovalExecutionSupportPort fallbackLoadWalletApprovalExecutionSupportPort() {
    return authorityAddress -> {
      throw unavailable();
    };
  }

  @Bean
  @ConditionalOnMissingBean(LoadWalletApprovalExecutionStatePort.class)
  LoadWalletApprovalExecutionStatePort fallbackLoadWalletApprovalExecutionStatePort() {
    return new LoadWalletApprovalExecutionStatePort() {
      @Override
      public Optional<WalletApprovalExecutionStateView> loadByExecutionIntentId(
          Long requesterUserId, String executionIntentId) {
        return Optional.empty();
      }

      @Override
      public Optional<WalletApprovalExecutionStateView> loadLatestByRegistrationId(
          String registrationId) {
        return Optional.empty();
      }
    };
  }

  @Bean
  @ConditionalOnMissingBean(CancelWalletApprovalExecutionPort.class)
  CancelWalletApprovalExecutionPort fallbackCancelWalletApprovalExecutionPort() {
    return (executionIntentId, errorCode, errorReason) -> {
      throw unavailable();
    };
  }

  @Bean
  @ConditionalOnMissingBean(SyncWalletApprovalExecutionSuccessPort.class)
  SyncWalletApprovalExecutionSuccessPort fallbackSyncWalletApprovalExecutionSuccessPort() {
    return transactionId -> {
      throw unavailable();
    };
  }

  private WalletApprovalUnavailableException unavailable() {
    return new WalletApprovalUnavailableException(DISABLED_REASON);
  }
}
