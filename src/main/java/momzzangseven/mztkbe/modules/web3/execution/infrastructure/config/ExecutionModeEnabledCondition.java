package momzzangseven.mztkbe.modules.web3.execution.infrastructure.config;

import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/** Matches when user-driven EIP-7702 execution or internal execution is enabled. */
public class ExecutionModeEnabledCondition extends AnyNestedCondition {

  ExecutionModeEnabledCondition() {
    super(ConfigurationPhase.REGISTER_BEAN);
  }

  @ConditionalOnProperty(prefix = "web3.eip7702", name = "enabled", havingValue = "true")
  static class UserExecutionEnabled {}

  @ConditionalOnProperty(prefix = "web3.execution.internal", name = "enabled", havingValue = "true")
  static class InternalExecutionEnabled {}
}
