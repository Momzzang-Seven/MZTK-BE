package momzzangseven.mztkbe.modules.level.infrastructure.external.reward;

import momzzangseven.mztkbe.global.error.web3.Web3InternalIssuerDisabledException;
import momzzangseven.mztkbe.modules.level.application.port.out.RewardMztkPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RewardMztkUnavailableConfig {

  private static final String MESSAGE =
      "LEVEL_UP_REWARD issuer is unavailable. Enable web3.reward-token.enabled or provide "
          + "RewardMztkPort.";

  @Bean
  @ConditionalOnMissingBean(RewardMztkPort.class)
  public RewardMztkPort rewardMztkPort() {
    return command -> {
      throw new Web3InternalIssuerDisabledException(MESSAGE);
    };
  }
}
