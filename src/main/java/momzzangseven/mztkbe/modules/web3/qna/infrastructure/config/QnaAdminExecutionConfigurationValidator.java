package momzzangseven.mztkbe.modules.web3.qna.infrastructure.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.LoadExecutionInternalIssuerPolicyPort;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.LoadQnaAdminSignerAddressPort;
import momzzangseven.mztkbe.modules.web3.qna.infrastructure.external.web3.QnaContractCallSupport;
import momzzangseven.mztkbe.modules.web3.shared.infrastructure.config.ConditionalOnInternalExecutionEnabled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnInternalExecutionEnabled
public class QnaAdminExecutionConfigurationValidator {

  private final LoadExecutionInternalIssuerPolicyPort loadExecutionInternalIssuerPolicyPort;
  private final LoadQnaAdminSignerAddressPort loadQnaAdminSignerAddressPort;
  private final QnaContractCallSupport qnaContractCallSupport;
  private final QnaEscrowProperties qnaEscrowProperties;

  @PostConstruct
  void validateOnStartup() {
    validateConfiguration();
  }

  void validateConfiguration() {
    LoadExecutionInternalIssuerPolicyPort.ExecutionInternalIssuerPolicy policy =
        loadExecutionInternalIssuerPolicyPort.loadPolicy();
    if (!policy.enabled()) {
      throw new IllegalStateException(
          "QnA admin execution requires web3.execution.internal-issuer.enabled=true");
    }
    if (!policy.qnaAdminSettleEnabled()) {
      throw new IllegalStateException(
          "QnA admin execution requires web3.execution.internal-issuer.action-types to include QNA_ADMIN_SETTLE");
    }
    if (!policy.qnaAdminRefundEnabled()) {
      throw new IllegalStateException(
          "QnA admin execution requires web3.execution.internal-issuer.action-types to include QNA_ADMIN_REFUND");
    }

    String signerAddress = loadQnaAdminSignerAddressPort.loadSignerAddress();
    try {
      if (!qnaContractCallSupport.isRelayerRegistered(
          qnaEscrowProperties.getQnaContractAddress(), signerAddress)) {
        throw new IllegalStateException(
            "QnA admin execution requires current server signer to be a registered relayer");
      }
    } catch (IllegalStateException e) {
      throw e;
    } catch (RuntimeException e) {
      throw new IllegalStateException(
          "QnA admin execution failed to validate current server signer relayer registration", e);
    }
  }
}
