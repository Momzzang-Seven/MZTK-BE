package momzzangseven.mztkbe.modules.web3.qna.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import momzzangseven.mztkbe.modules.web3.qna.application.port.out.LoadExecutionInternalIssuerPolicyPort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.LoadExecutionInternalIssuerPolicyPort.ExecutionInternalIssuerPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class QnaAutoAcceptConfigurationValidatorTest {

  @Test
  @DisplayName("auto-accept가 꺼져 있으면 internal issuer 설정을 강제하지 않는다")
  void validateConfiguration_allowsDisabledAutoAccept() {
    QnaAutoAcceptProperties qnaAutoAcceptProperties = new QnaAutoAcceptProperties();
    qnaAutoAcceptProperties.setEnabled(false);

    LoadExecutionInternalIssuerPolicyPort loadExecutionInternalIssuerPolicyPort =
        () -> new ExecutionInternalIssuerPolicy(false, false);

    QnaAutoAcceptConfigurationValidator validator =
        new QnaAutoAcceptConfigurationValidator(
            qnaAutoAcceptProperties, loadExecutionInternalIssuerPolicyPort);

    assertThatCode(validator::validateConfiguration).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("auto-accept가 켜져 있으면 internal issuer도 활성화되어야 한다")
  void validateConfiguration_rejectsDisabledInternalIssuer() {
    QnaAutoAcceptProperties qnaAutoAcceptProperties = new QnaAutoAcceptProperties();
    qnaAutoAcceptProperties.setEnabled(true);

    LoadExecutionInternalIssuerPolicyPort loadExecutionInternalIssuerPolicyPort =
        () -> new ExecutionInternalIssuerPolicy(false, true);

    QnaAutoAcceptConfigurationValidator validator =
        new QnaAutoAcceptConfigurationValidator(
            qnaAutoAcceptProperties, loadExecutionInternalIssuerPolicyPort);

    assertThatThrownBy(validator::validateConfiguration)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("internal-issuer.enabled=true");
  }

  @Test
  @DisplayName("auto-accept가 켜져 있으면 internal issuer action-types에 QNA_ADMIN_SETTLE이 포함되어야 한다")
  void validateConfiguration_rejectsMissingAdminSettleActionType() {
    QnaAutoAcceptProperties qnaAutoAcceptProperties = new QnaAutoAcceptProperties();
    qnaAutoAcceptProperties.setEnabled(true);

    LoadExecutionInternalIssuerPolicyPort loadExecutionInternalIssuerPolicyPort =
        () -> new ExecutionInternalIssuerPolicy(true, false);

    QnaAutoAcceptConfigurationValidator validator =
        new QnaAutoAcceptConfigurationValidator(
            qnaAutoAcceptProperties, loadExecutionInternalIssuerPolicyPort);

    assertThatThrownBy(validator::validateConfiguration)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("QNA_ADMIN_SETTLE");
  }
}
