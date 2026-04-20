package momzzangseven.mztkbe.modules.web3.shared.infrastructure.config;

import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

class ExecutionModeEnabledCondition extends AnyNestedCondition {

  ExecutionModeEnabledCondition() {
    super(ConfigurationPhase.REGISTER_BEAN);
  }

  @ConditionalOnProperty(prefix = "web3.eip7702", name = "enabled", havingValue = "true")
  static class UserExecutionEnabled {}

  @ConditionalOnProperty(
      prefix = "web3.execution.internal-issuer",
      name = "enabled",
      havingValue = "true")
  static class InternalExecutionEnabled {}
}
