package momzzangseven.mztkbe.modules.web3.qna.infrastructure.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import momzzangseven.mztkbe.modules.web3.qna.application.port.out.LoadExecutionInternalIssuerPolicyPort;
import momzzangseven.mztkbe.modules.web3.qna.infrastructure.external.web3.QnaContractCallSupport;
import momzzangseven.mztkbe.modules.web3.shared.application.port.in.ProbeExecutionSignerCapabilityUseCase;
import momzzangseven.mztkbe.modules.web3.shared.application.port.out.LoadExecutionSignerConfigPort;
import momzzangseven.mztkbe.modules.web3.shared.application.port.out.ProbeExecutionSignerCapabilityPort;
import momzzangseven.mztkbe.modules.web3.shared.infrastructure.config.ConditionalOnQnaAdminEnabled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
@ConditionalOnQnaAdminEnabled
@ConditionalOnBean({LoadExecutionSignerConfigPort.class, ProbeExecutionSignerCapabilityPort.class})
public class QnaAdminExecutionConfigurationValidator {

  private final LoadExecutionInternalIssuerPolicyPort loadExecutionInternalIssuerPolicyPort;
  private final ProbeExecutionSignerCapabilityUseCase probeExecutionSignerCapabilityUseCase;
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
          "QnA admin execution requires web3.execution.internal.enabled=true");
    }
    if (!policy.qnaAdminSettleEnabled()) {
      throw new IllegalStateException(
          "QnA admin execution requires web3.execution.internal.action-policy to enable QNA_ADMIN_SETTLE");
    }
    if (!policy.qnaAdminRefundEnabled()) {
      throw new IllegalStateException(
          "QnA admin execution requires web3.execution.internal.action-policy to enable QNA_ADMIN_REFUND");
    }

    var serverSigner = probeExecutionSignerCapabilityUseCase.execute();
    if (!serverSigner.signable() || serverSigner.signerAddress() == null) {
      log.warn(
          "QnA admin execution signer is unavailable at startup: walletAlias={}, slotStatus={}, failureReason={}",
          serverSigner.walletAlias(),
          serverSigner.slotStatus(),
          serverSigner.failureReason());
      return;
    }
    try {
      if (!qnaContractCallSupport.isRelayerRegistered(
          qnaEscrowProperties.getQnaContractAddress(), serverSigner.signerAddress())) {
        log.warn(
            "QnA admin execution signer is not registered as relayer at startup: signerAddress={}",
            serverSigner.signerAddress());
      }
    } catch (RuntimeException e) {
      log.warn(
          "QnA admin execution failed to validate current server signer relayer registration at startup",
          e);
    }
  }
}
