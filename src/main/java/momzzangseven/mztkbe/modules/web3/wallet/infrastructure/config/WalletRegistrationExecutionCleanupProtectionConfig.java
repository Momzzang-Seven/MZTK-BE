package momzzangseven.mztkbe.modules.web3.wallet.infrastructure.config;

import momzzangseven.mztkbe.modules.web3.wallet.application.port.in.FilterWalletRegistrationExecutionCleanupCandidatesUseCase;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LoadWalletRegistrationSessionPort;
import momzzangseven.mztkbe.modules.web3.wallet.application.service.WalletRegistrationExecutionCleanupProtectionService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class WalletRegistrationExecutionCleanupProtectionConfig {

  @Bean
  @ConditionalOnProperty(
      prefix = "web3",
      name = {"eip7702.enabled", "eip7702.cleanup.enabled"},
      havingValue = "true")
  @ConditionalOnBean({LoadWalletRegistrationSessionPort.class, PlatformTransactionManager.class})
  FilterWalletRegistrationExecutionCleanupCandidatesUseCase
      filterWalletRegistrationExecutionCleanupCandidatesUseCase(
          LoadWalletRegistrationSessionPort loadSessionPort,
          PlatformTransactionManager transactionManager) {
    return new TransactionalWalletRegistrationExecutionCleanupProtectionUseCase(
        new WalletRegistrationExecutionCleanupProtectionService(loadSessionPort),
        transactionManager);
  }
}
