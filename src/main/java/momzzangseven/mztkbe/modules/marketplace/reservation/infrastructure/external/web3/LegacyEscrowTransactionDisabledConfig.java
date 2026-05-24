package momzzangseven.mztkbe.modules.marketplace.reservation.infrastructure.external.web3;

import momzzangseven.mztkbe.modules.marketplace.reservation.application.port.out.SubmitEscrowTransactionPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LegacyEscrowTransactionDisabledConfig {

  @Bean
  @ConditionalOnMissingBean(SubmitEscrowTransactionPort.class)
  public SubmitEscrowTransactionPort legacyEscrowTransactionDisabledAdapter() {
    return new LegacyEscrowTransactionDisabledAdapter();
  }
}
