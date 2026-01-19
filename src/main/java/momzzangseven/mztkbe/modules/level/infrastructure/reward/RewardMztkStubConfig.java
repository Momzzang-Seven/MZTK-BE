package momzzangseven.mztkbe.modules.level.infrastructure.reward;

import momzzangseven.mztkbe.modules.level.application.port.out.RewardMztkPort;
import momzzangseven.mztkbe.modules.level.application.port.out.RewardMztkResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RewardMztkStubConfig {

  @Bean
  @ConditionalOnMissingBean(RewardMztkPort.class)
  public RewardMztkPort rewardMztkPort() {
    return command -> RewardMztkResult.pending("NOT_IMPLEMENTED");
  }
}
