package momzzangseven.mztkbe.modules.web3.treasury.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import momzzangseven.mztkbe.modules.web3.treasury.application.dto.DisableKmsKeyCommand;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.KmsAuditAction;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.KmsKeyLifecyclePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DisableKmsKeyServiceTest {

  @Mock private KmsKeyLifecyclePort kmsKeyLifecyclePort;
  @Mock private KmsAuditRecorder kmsAuditRecorder;

  private DisableKmsKeyService service;

  private static final String ALIAS = "reward-treasury";
  private static final String KMS_KEY_ID = "kms-key-1";
  private static final String ADDRESS = "0x" + "a".repeat(40);
  private static final Long OPERATOR_ID = 7L;

  @BeforeEach
  void setUp() {
    service = new DisableKmsKeyService(kmsKeyLifecyclePort, kmsAuditRecorder);
  }

  @Test
  void execute_recordsSuccess_whenKmsCallSucceeds() {
    service.execute(new DisableKmsKeyCommand(ALIAS, KMS_KEY_ID, ADDRESS, OPERATOR_ID));

    verify(kmsKeyLifecyclePort).disableKey(KMS_KEY_ID);
    verify(kmsAuditRecorder)
        .record(OPERATOR_ID, ALIAS, KMS_KEY_ID, ADDRESS, KmsAuditAction.KMS_DISABLE, true, null);
    verifyNoMoreInteractions(kmsAuditRecorder);
  }

  @Test
  void execute_recordsFailureAndRethrows_whenKmsCallThrows() {
    RuntimeException kmsFailure = new IllegalStateException("KMS down");
    doThrow(kmsFailure).when(kmsKeyLifecyclePort).disableKey(KMS_KEY_ID);

    assertThatThrownBy(
            () -> service.execute(new DisableKmsKeyCommand(ALIAS, KMS_KEY_ID, ADDRESS, OPERATOR_ID)))
        .isSameAs(kmsFailure);

    verify(kmsAuditRecorder)
        .record(
            OPERATOR_ID,
            ALIAS,
            KMS_KEY_ID,
            ADDRESS,
            KmsAuditAction.KMS_DISABLE,
            false,
            "IllegalStateException");
  }
}
