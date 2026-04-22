package momzzangseven.mztkbe.modules.web3.qna.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import momzzangseven.mztkbe.modules.web3.qna.application.dto.QnaAdminRelayerRegistrationStatus;
import momzzangseven.mztkbe.modules.web3.qna.infrastructure.external.web3.QnaContractCallSupport;
import momzzangseven.mztkbe.modules.web3.shared.application.dto.ExecutionSignerCapabilityView;
import momzzangseven.mztkbe.modules.web3.shared.application.port.in.ProbeExecutionSignerCapabilityUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;

@ExtendWith(MockitoExtension.class)
class QnaAdminExecutionHealthIndicatorTest {

  @Mock private ProbeExecutionSignerCapabilityUseCase probeExecutionSignerCapabilityUseCase;
  @Mock private QnaContractCallSupport qnaContractCallSupport;

  private QnaEscrowProperties qnaEscrowProperties;
  private QnaAdminExecutionHealthIndicator indicator;

  @BeforeEach
  void setUp() {
    qnaEscrowProperties = new QnaEscrowProperties();
    qnaEscrowProperties.setQnaContractAddress("0x" + "1".repeat(40));
    indicator =
        new QnaAdminExecutionHealthIndicator(
            probeExecutionSignerCapabilityUseCase, qnaContractCallSupport, qnaEscrowProperties);
  }

  @Test
  void health_keepsUpWhenRelayerCheckThrows() {
    when(probeExecutionSignerCapabilityUseCase.execute())
        .thenReturn(ExecutionSignerCapabilityView.ready("sponsor-treasury", "0x" + "2".repeat(40)));
    when(qnaContractCallSupport.isRelayerRegistered("0x" + "1".repeat(40), "0x" + "2".repeat(40)))
        .thenThrow(new IllegalStateException("rpc down"));

    Health health = indicator.health();

    assertThat(health.getStatus().getCode()).isEqualTo("UP");
    assertThat(health.getDetails()).containsEntry("relayerRegistered", false);
    assertThat(health.getDetails())
        .containsEntry(
            "relayerRegistrationStatus", QnaAdminRelayerRegistrationStatus.CHECK_FAILED);
    assertThat(health.getDetails())
        .containsEntry(
            "relayerRegistrationCheckError", IllegalStateException.class.getSimpleName());
  }

  @Test
  void health_reportsRegisteredStatusWhenRelayerIsRegistered() {
    when(probeExecutionSignerCapabilityUseCase.execute())
        .thenReturn(ExecutionSignerCapabilityView.ready("sponsor-treasury", "0x" + "2".repeat(40)));
    when(qnaContractCallSupport.isRelayerRegistered("0x" + "1".repeat(40), "0x" + "2".repeat(40)))
        .thenReturn(true);

    Health health = indicator.health();

    assertThat(health.getDetails()).containsEntry("relayerRegistered", true);
    assertThat(health.getDetails())
        .containsEntry("relayerRegistrationStatus", QnaAdminRelayerRegistrationStatus.REGISTERED);
  }
}
