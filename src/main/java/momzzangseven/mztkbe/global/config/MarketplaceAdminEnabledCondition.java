package momzzangseven.mztkbe.global.config;

import org.springframework.boot.autoconfigure.condition.AllNestedConditions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

final class MarketplaceAdminEnabledCondition extends AllNestedConditions {

  MarketplaceAdminEnabledCondition() {
    super(ConfigurationPhase.REGISTER_BEAN);
  }

  @ConditionalOnProperty(prefix = "web3.execution.internal", name = "enabled", havingValue = "true")
  static class InternalExecutionEnabled {}

  @ConditionalOnProperty(prefix = "web3.marketplace.admin", name = "enabled", havingValue = "true")
  static class MarketplaceAdminEnabled {}
}
