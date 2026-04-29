package momzzangseven.mztkbe.modules.web3.treasury.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import momzzangseven.mztkbe.modules.web3.treasury.application.dto.KmsAuditAction;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.ScheduleKmsKeyDeletionCommand;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.KmsKeyLifecyclePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScheduleKmsKeyDeletionServiceTest {

  private static final String ALIAS = "reward-treasury";
  private static final String KMS_KEY_ID = "kms-key-1";
  private static final String ADDRESS = "0x" + "a".repeat(40);
  private static final Long OPERATOR_ID = 7L;
  private static final int WINDOW_DAYS = 30;

  @Mock private KmsKeyLifecyclePort kmsKeyLifecyclePort;
  @Mock private KmsAuditRecorder kmsAuditRecorder;

  private ScheduleKmsKeyDeletionService service;

  @BeforeEach
  void setUp() {
    service = new ScheduleKmsKeyDeletionService(kmsKeyLifecyclePort, kmsAuditRecorder);
  }

  @Test
  void execute_recordsSuccess_whenKmsCallSucceeds() {
    service.execute(
        new ScheduleKmsKeyDeletionCommand(ALIAS, KMS_KEY_ID, ADDRESS, OPERATOR_ID, WINDOW_DAYS));

    verify(kmsKeyLifecyclePort).scheduleKeyDeletion(KMS_KEY_ID, WINDOW_DAYS);
    verify(kmsAuditRecorder)
        .record(
            OPERATOR_ID,
            ALIAS,
            KMS_KEY_ID,
            ADDRESS,
            KmsAuditAction.KMS_SCHEDULE_DELETION,
            true,
            null);
    verifyNoMoreInteractions(kmsAuditRecorder);
  }

  @Test
  void execute_recordsFailureAndRethrows_whenKmsCallThrows() {
    RuntimeException failure = new IllegalStateException("KMS down");
    doThrow(failure).when(kmsKeyLifecyclePort).scheduleKeyDeletion(KMS_KEY_ID, WINDOW_DAYS);

    assertThatThrownBy(
            () ->
                service.execute(
                    new ScheduleKmsKeyDeletionCommand(
                        ALIAS, KMS_KEY_ID, ADDRESS, OPERATOR_ID, WINDOW_DAYS)))
        .isSameAs(failure);

    verify(kmsAuditRecorder)
        .record(
            OPERATOR_ID,
            ALIAS,
            KMS_KEY_ID,
            ADDRESS,
            KmsAuditAction.KMS_SCHEDULE_DELETION,
            false,
            "IllegalStateException");
  }
}
