package momzzangseven.mztkbe.modules.web3.transaction.infrastructure.config;

import momzzangseven.mztkbe.modules.web3.transaction.application.port.out.RunTransactionStateUpdatePort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Transaction boundary beans for web3 transaction state updates. */
@Configuration
public class TransactionStateUpdateConfig {

  /** Provides a REQUIRES_NEW boundary for short state mutation phases. */
  @Bean
  RunTransactionStateUpdatePort runTransactionStateUpdatePort(
      PlatformTransactionManager transactionManager) {
    TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
    transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    return new RunTransactionStateUpdatePort() {
      @Override
      public <T> T requiresNew(java.util.function.Supplier<T> action) {
        return transactionTemplate.execute(status -> action.get());
      }
    };
  }
}
