package momzzangseven.mztkbe.modules.web3.shared.infrastructure.config;

import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

class RewardTokenOrExecutionEnabledCondition extends AnyNestedCondition {

  RewardTokenOrExecutionEnabledCondition() {
    super(ConfigurationPhase.REGISTER_BEAN);
  }

  @ConditionalOnProperty(prefix = "web3.reward-token", name = "enabled", havingValue = "true")
  static class RewardTokenEnabled {}

  @ConditionalOnProperty(prefix = "web3.eip7702", name = "enabled", havingValue = "true")
  static class UserExecutionEnabled {}

  @ConditionalOnProperty(prefix = "web3.execution.internal", name = "enabled", havingValue = "true")
  static class InternalExecutionEnabled {}
}
