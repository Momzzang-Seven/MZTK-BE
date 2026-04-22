package momzzangseven.mztkbe.modules.web3.qna.infrastructure.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadInternalExecutionIssuerPolicyPort;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionActionType;
import momzzangseven.mztkbe.modules.web3.shared.infrastructure.config.ConditionalOnQnaAutoAcceptEnabled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnQnaAutoAcceptEnabled
public class QnaAutoAcceptConfigurationValidator {

  private final QnaAutoAcceptProperties qnaAutoAcceptProperties;
  private final LoadInternalExecutionIssuerPolicyPort loadInternalExecutionIssuerPolicyPort;

  @PostConstruct
  void validateOnStartup() {
    validateConfiguration();
  }

  void validateConfiguration() {
    if (!Boolean.TRUE.equals(qnaAutoAcceptProperties.getEnabled())) {
      return;
    }
    LoadInternalExecutionIssuerPolicyPort.InternalExecutionIssuerPolicy policy =
        loadInternalExecutionIssuerPolicyPort.loadPolicy();
    if (!policy.enabled()) {
      throw new IllegalStateException(
          "web3.qna.auto-accept.enabled=true requires web3.execution.internal.enabled=true");
    }
    if (!policy.actionTypes().contains(ExecutionActionType.QNA_ADMIN_SETTLE)) {
      throw new IllegalStateException(
          "web3.qna.auto-accept.enabled=true requires web3.execution.internal.action-policy to enable QNA_ADMIN_SETTLE");
    }
  }
}
