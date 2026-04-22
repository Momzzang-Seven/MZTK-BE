package momzzangseven.mztkbe.modules.web3.token.infrastructure.config;

import momzzangseven.mztkbe.modules.web3.shared.infrastructure.config.ConditionalOnAnyExecutionEnabled;
import momzzangseven.mztkbe.modules.web3.token.application.port.in.LoadTreasuryKeyMaterialUseCase;
import momzzangseven.mztkbe.modules.web3.token.application.port.out.LoadTreasuryKeyPort;
import momzzangseven.mztkbe.modules.web3.token.application.service.LoadTreasuryKeyMaterialService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnAnyExecutionEnabled
public class TreasuryKeyMaterialServiceConfig {

  @Bean
  LoadTreasuryKeyMaterialUseCase loadTreasuryKeyMaterialUseCase(
      LoadTreasuryKeyPort loadTreasuryKeyPort) {
    return new LoadTreasuryKeyMaterialService(loadTreasuryKeyPort);
  }
}
