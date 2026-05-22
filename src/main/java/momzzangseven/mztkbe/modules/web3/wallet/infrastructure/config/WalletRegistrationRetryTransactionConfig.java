package momzzangseven.mztkbe.modules.web3.wallet.infrastructure.config;

import java.util.function.Supplier;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.RunWalletRegistrationRetryTransactionPort;
import momzzangseven.mztkbe.modules.web3.wallet.application.port.out.RunWalletRegistrationTransactionPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
public class WalletRegistrationRetryTransactionConfig {

  @Bean
  @ConditionalOnMissingBean(RunWalletRegistrationTransactionPort.class)
  RunWalletRegistrationTransactionPort runWalletRegistrationTransactionPort(
      PlatformTransactionManager transactionManager) {
    TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
    return new RunWalletRegistrationTransactionPort() {
      @Override
      public <T> T execute(Supplier<T> callback) {
        return transactionTemplate.execute(status -> callback.get());
      }
    };
  }

  @Bean
  @ConditionalOnMissingBean(RunWalletRegistrationRetryTransactionPort.class)
  RunWalletRegistrationRetryTransactionPort runWalletRegistrationRetryTransactionPort(
      PlatformTransactionManager transactionManager) {
    TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
    return new RunWalletRegistrationRetryTransactionPort() {
      @Override
      public <T> T execute(Supplier<T> callback) {
        return transactionTemplate.execute(status -> callback.get());
      }
    };
  }
}
