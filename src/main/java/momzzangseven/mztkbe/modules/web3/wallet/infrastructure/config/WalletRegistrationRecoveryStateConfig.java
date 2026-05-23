package momzzangseven.mztkbe.modules.web3.wallet.infrastructure.config;

import momzzangseven.mztkbe.modules.web3.wallet.application.port.in.LoadWalletRegistrationRecoveryStateUseCase;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.LoadWalletRegistrationSessionPort;
import momzzangseven.mztkbe.modules.web3.wallet.application.service.LoadWalletRegistrationRecoveryStateService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class WalletRegistrationRecoveryStateConfig {

  @Bean
  @ConditionalOnBean({LoadWalletRegistrationSessionPort.class, PlatformTransactionManager.class})
  LoadWalletRegistrationRecoveryStateUseCase loadWalletRegistrationRecoveryStateUseCase(
      LoadWalletRegistrationSessionPort loadSessionPort,
      PlatformTransactionManager transactionManager) {
    return new TransactionalLoadWalletRegistrationRecoveryStateUseCase(
        new LoadWalletRegistrationRecoveryStateService(loadSessionPort), transactionManager);
  }
}
