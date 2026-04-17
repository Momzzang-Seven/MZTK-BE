package momzzangseven.mztkbe.modules.web3.qna.infrastructure.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.LoadExecutionInternalIssuerPolicyPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "web3",
    name = {"eip7702.enabled", "reward-token.enabled"},
    havingValue = "true")
public class QnaAutoAcceptConfigurationValidator {

  private final QnaAutoAcceptProperties qnaAutoAcceptProperties;
  private final LoadExecutionInternalIssuerPolicyPort loadExecutionInternalIssuerPolicyPort;

  @PostConstruct
  void validateOnStartup() {
    validateConfiguration();
  }

  void validateConfiguration() {
    if (!Boolean.TRUE.equals(qnaAutoAcceptProperties.getEnabled())) {
      return;
    }
    LoadExecutionInternalIssuerPolicyPort.ExecutionInternalIssuerPolicy policy =
        loadExecutionInternalIssuerPolicyPort.loadPolicy();
    if (!policy.enabled()) {
      throw new IllegalStateException(
          "web3.qna.auto-accept.enabled=true requires web3.execution.internal-issuer.enabled=true");
    }
    if (!policy.qnaAdminSettleEnabled()) {
      throw new IllegalStateException(
          "web3.qna.auto-accept.enabled=true requires web3.execution.internal-issuer.action-types to include QNA_ADMIN_SETTLE");
    }
  }
}
