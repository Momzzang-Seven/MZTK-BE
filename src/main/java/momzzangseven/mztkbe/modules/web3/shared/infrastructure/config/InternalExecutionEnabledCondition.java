package momzzangseven.mztkbe.modules.web3.shared.infrastructure.config;

import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

class InternalExecutionEnabledCondition extends AnyNestedCondition {

  InternalExecutionEnabledCondition() {
    super(ConfigurationPhase.REGISTER_BEAN);
  }

  @ConditionalOnProperty(prefix = "web3.execution.internal", name = "enabled", havingValue = "true")
  static class InternalExecutionEnabled {}
}
