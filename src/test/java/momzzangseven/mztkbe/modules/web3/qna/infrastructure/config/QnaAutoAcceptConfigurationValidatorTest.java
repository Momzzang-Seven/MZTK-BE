package momzzangseven.mztkbe.modules.web3.qna.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadInternalExecutionIssuerPolicyPort;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.LoadInternalExecutionIssuerPolicyPort.InternalExecutionIssuerPolicy;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionActionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class QnaAutoAcceptConfigurationValidatorTest {

  @Test
  @DisplayName("auto-accept가 꺼져 있으면 internal issuer 설정을 강제하지 않는다")
  void validateConfiguration_allowsDisabledAutoAccept() {
    QnaAutoAcceptProperties qnaAutoAcceptProperties = new QnaAutoAcceptProperties();
    qnaAutoAcceptProperties.setEnabled(false);

    LoadInternalExecutionIssuerPolicyPort loadInternalExecutionIssuerPolicyPort =
        () -> new InternalExecutionIssuerPolicy(false, 20, List.of());

    QnaAutoAcceptConfigurationValidator validator =
        new QnaAutoAcceptConfigurationValidator(
            qnaAutoAcceptProperties, loadInternalExecutionIssuerPolicyPort);

    assertThatCode(validator::validateConfiguration).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("auto-accept가 켜져 있으면 internal issuer도 활성화되어야 한다")
  void validateConfiguration_rejectsDisabledInternalIssuer() {
    QnaAutoAcceptProperties qnaAutoAcceptProperties = new QnaAutoAcceptProperties();
    qnaAutoAcceptProperties.setEnabled(true);

    LoadInternalExecutionIssuerPolicyPort loadInternalExecutionIssuerPolicyPort =
        () ->
            new InternalExecutionIssuerPolicy(
                false, 20, List.of(ExecutionActionType.QNA_ADMIN_SETTLE));

    QnaAutoAcceptConfigurationValidator validator =
        new QnaAutoAcceptConfigurationValidator(
            qnaAutoAcceptProperties, loadInternalExecutionIssuerPolicyPort);

    assertThatThrownBy(validator::validateConfiguration)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("execution.internal.enabled=true");
  }

  @Test
  @DisplayName("auto-accept가 켜져 있으면 internal action-policy가 QNA_ADMIN_SETTLE을 포함해야 한다")
  void validateConfiguration_rejectsMissingAdminSettleActionType() {
    QnaAutoAcceptProperties qnaAutoAcceptProperties = new QnaAutoAcceptProperties();
    qnaAutoAcceptProperties.setEnabled(true);

    LoadInternalExecutionIssuerPolicyPort loadInternalExecutionIssuerPolicyPort =
        () -> new InternalExecutionIssuerPolicy(true, 20, List.of());

    QnaAutoAcceptConfigurationValidator validator =
        new QnaAutoAcceptConfigurationValidator(
            qnaAutoAcceptProperties, loadInternalExecutionIssuerPolicyPort);

    assertThatThrownBy(validator::validateConfiguration)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("QNA_ADMIN_SETTLE");
  }
}
