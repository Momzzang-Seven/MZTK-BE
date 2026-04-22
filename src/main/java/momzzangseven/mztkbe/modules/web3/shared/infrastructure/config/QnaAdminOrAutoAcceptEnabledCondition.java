package momzzangseven.mztkbe.modules.web3.shared.infrastructure.config;

import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;

class QnaAdminOrAutoAcceptEnabledCondition extends AnyNestedCondition {

  QnaAdminOrAutoAcceptEnabledCondition() {
    super(ConfigurationPhase.REGISTER_BEAN);
  }

  @ConditionalOnQnaAdminEnabled
  static class QnaAdminEnabled {}

  @ConditionalOnQnaAutoAcceptEnabled
  static class QnaAutoAcceptEnabled {}
}
