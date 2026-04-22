package momzzangseven.mztkbe.modules.web3.qna.infrastructure.config;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaAdminRelayerRegistrationStatus;
import momzzangseven.mztkbe.modules.web3.qna.infrastructure.external.web3.QnaContractCallSupport;
import momzzangseven.mztkbe.modules.web3.shared.application.port.in.ProbeExecutionSignerCapabilityUseCase;
import momzzangseven.mztkbe.modules.web3.shared.application.port.out.LoadExecutionSignerConfigPort;
import momzzangseven.mztkbe.modules.web3.shared.application.port.out.ProbeExecutionSignerCapabilityPort;
import momzzangseven.mztkbe.modules.web3.shared.infrastructure.config.ConditionalOnQnaAdminEnabled;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

@Component("qnaAdminExecution")
@RequiredArgsConstructor
@ConditionalOnQnaAdminEnabled
@ConditionalOnBean({LoadExecutionSignerConfigPort.class, ProbeExecutionSignerCapabilityPort.class})
public class QnaAdminExecutionHealthIndicator implements HealthIndicator {

  private final ProbeExecutionSignerCapabilityUseCase probeExecutionSignerCapabilityUseCase;
  private final QnaContractCallSupport qnaContractCallSupport;
  private final QnaEscrowProperties qnaEscrowProperties;

  @Override
  public Health health() {
    var signer = probeExecutionSignerCapabilityUseCase.execute();
    Map<String, Object> details = new LinkedHashMap<>();
    details.put("walletAlias", signer.walletAlias());
    details.put("slotStatus", signer.slotStatus());
    details.put("failureReason", signer.failureReason());
    details.put("currentServerSignerAddress", signer.signerAddress());
    details.put("signable", signer.signable());

    boolean relayerRegistered = false;
    QnaAdminRelayerRegistrationStatus relayerRegistrationStatus =
        QnaAdminRelayerRegistrationStatus.UNCHECKED;
    if (signer.signable() && signer.signerAddress() != null) {
      try {
        relayerRegistered =
            qnaContractCallSupport.isRelayerRegistered(
                qnaEscrowProperties.getQnaContractAddress(), signer.signerAddress());
        relayerRegistrationStatus =
            relayerRegistered
                ? QnaAdminRelayerRegistrationStatus.REGISTERED
                : QnaAdminRelayerRegistrationStatus.NOT_REGISTERED;
      } catch (RuntimeException e) {
        relayerRegistrationStatus = QnaAdminRelayerRegistrationStatus.CHECK_FAILED;
        details.put("relayerRegistrationCheckError", e.getClass().getSimpleName());
      }
    }
    details.put("relayerRegistered", relayerRegistered);
    details.put("relayerRegistrationStatus", relayerRegistrationStatus);
    return Health.up().withDetails(details).build();
  }
}
