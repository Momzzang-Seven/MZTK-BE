package momzzangseven.mztkbe.modules.web3.shared.infrastructure.config;

import momzzangseven.mztkbe.modules.web3.shared.application.port.in.GetRewardTreasurySignerConfigUseCase;
import momzzangseven.mztkbe.modules.web3.shared.application.port.in.GetSponsorTreasurySignerConfigUseCase;
import momzzangseven.mztkbe.modules.web3.shared.application.port.out.LoadRewardTreasurySignerConfigSourcePort;
import momzzangseven.mztkbe.modules.web3.shared.application.port.out.LoadSponsorTreasurySignerConfigSourcePort;
import momzzangseven.mztkbe.modules.web3.shared.application.service.GetRewardTreasurySignerConfigService;
import momzzangseven.mztkbe.modules.web3.shared.application.service.GetSponsorTreasurySignerConfigService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TreasurySignerConfigUseCaseConfig {

  @Bean
  GetRewardTreasurySignerConfigUseCase getRewardTreasurySignerConfigUseCase(
      LoadRewardTreasurySignerConfigSourcePort loadRewardTreasurySignerConfigSourcePort) {
    return new GetRewardTreasurySignerConfigService(loadRewardTreasurySignerConfigSourcePort);
  }

  @Bean
  GetSponsorTreasurySignerConfigUseCase getSponsorTreasurySignerConfigUseCase(
      LoadSponsorTreasurySignerConfigSourcePort loadSponsorTreasurySignerConfigSourcePort) {
    return new GetSponsorTreasurySignerConfigService(loadSponsorTreasurySignerConfigSourcePort);
  }
}
