package momzzangseven.mztkbe.modules.web3.treasury.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import momzzangseven.mztkbe.modules.web3.treasury.application.dto.KmsAuditAction;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.KmsAuditPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KmsAuditRecorderTest {

  @Mock private KmsAuditPort kmsAuditPort;

  private KmsAuditRecorder recorder;

  @BeforeEach
  void setUp() {
    recorder = new KmsAuditRecorder(kmsAuditPort);
  }

  @Test
  void record_delegatesToPort_withMappedFields() {
    recorder.record(
        7L,
        "reward-treasury",
        "kms-key-1",
        "0x" + "a".repeat(40),
        KmsAuditAction.KMS_DISABLE,
        true,
        null);

    ArgumentCaptor<KmsAuditPort.AuditCommand> captor =
        ArgumentCaptor.forClass(KmsAuditPort.AuditCommand.class);
    verify(kmsAuditPort).record(captor.capture());
    KmsAuditPort.AuditCommand cmd = captor.getValue();
    assertThat(cmd.operatorId()).isEqualTo(7L);
    assertThat(cmd.walletAlias()).isEqualTo("reward-treasury");
    assertThat(cmd.kmsKeyId()).isEqualTo("kms-key-1");
    assertThat(cmd.action()).isEqualTo(KmsAuditAction.KMS_DISABLE);
    assertThat(cmd.success()).isTrue();
  }

  @Test
  void record_swallowsPortExceptions_soOriginalErrorIsNotMasked() {
    doThrow(new RuntimeException("DB down")).when(kmsAuditPort).record(any());

    assertThatCode(
            () ->
                recorder.record(
                    7L,
                    "reward-treasury",
                    "kms-key-1",
                    null,
                    KmsAuditAction.KMS_CREATE_ALIAS,
                    false,
                    "AwsServiceException"))
        .doesNotThrowAnyException();
  }
}
