package momzzangseven.mztkbe.modules.web3.treasury.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import momzzangseven.mztkbe.global.error.treasury.KmsAliasAlreadyExistsException;
import momzzangseven.mztkbe.global.error.treasury.TreasuryWalletAlreadyProvisionedException;
import momzzangseven.mztkbe.modules.web3.shared.domain.crypto.KmsKeyState;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.BindKmsAliasCommand;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.KmsAuditAction;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.KmsKeyLifecyclePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BindKmsAliasServiceTest {

  private static final String ALIAS = "reward-treasury";
  private static final String KMS_KEY_ID = "kms-key-1";
  private static final String ADDRESS = "0x" + "a".repeat(40);
  private static final Long OPERATOR_ID = 7L;

  @Mock private KmsKeyLifecyclePort kmsKeyLifecyclePort;
  @Mock private KmsAuditRecorder kmsAuditRecorder;

  private BindKmsAliasService service;

  @BeforeEach
  void setUp() {
    service = new BindKmsAliasService(kmsKeyLifecyclePort, kmsAuditRecorder);
  }

  private BindKmsAliasCommand cmd() {
    return new BindKmsAliasCommand(ALIAS, KMS_KEY_ID, ADDRESS, OPERATOR_ID);
  }

  @Test
  void execute_recordsCreateAliasSuccess_whenCreateAliasReturnsCleanly() {
    service.execute(cmd());

    verify(kmsKeyLifecyclePort).createAlias(ALIAS, KMS_KEY_ID);
    verify(kmsAuditRecorder)
        .record(
            OPERATOR_ID, ALIAS, KMS_KEY_ID, ADDRESS, KmsAuditAction.KMS_CREATE_ALIAS, true, null);
  }

  @Test
  void execute_recoversWithUpdateAlias_whenAliasIsGhostFromPreviousRun() {
    doThrow(new KmsAliasAlreadyExistsException("alias taken"))
        .when(kmsKeyLifecyclePort)
        .createAlias(ALIAS, KMS_KEY_ID);
    when(kmsKeyLifecyclePort.describeAliasTarget(ALIAS)).thenReturn(KmsKeyState.PENDING_DELETION);

    service.execute(cmd());

    verify(kmsKeyLifecyclePort).updateAlias(ALIAS, KMS_KEY_ID);
    verify(kmsAuditRecorder)
        .record(
            OPERATOR_ID, ALIAS, KMS_KEY_ID, ADDRESS, KmsAuditAction.KMS_UPDATE_ALIAS, true, null);
  }

  @Test
  void execute_recoversWithUpdateAlias_whenAliasMissing() {
    doThrow(new KmsAliasAlreadyExistsException("alias taken"))
        .when(kmsKeyLifecyclePort)
        .createAlias(ALIAS, KMS_KEY_ID);
    when(kmsKeyLifecyclePort.describeAliasTarget(ALIAS)).thenReturn(KmsKeyState.UNAVAILABLE);

    service.execute(cmd());

    verify(kmsKeyLifecyclePort).updateAlias(ALIAS, KMS_KEY_ID);
  }

  @Test
  void execute_throwsAlreadyProvisioned_whenAliasPointsToEnabledKey() {
    doThrow(new KmsAliasAlreadyExistsException("alias taken"))
        .when(kmsKeyLifecyclePort)
        .createAlias(ALIAS, KMS_KEY_ID);
    when(kmsKeyLifecyclePort.describeAliasTarget(ALIAS)).thenReturn(KmsKeyState.ENABLED);

    assertThatThrownBy(() -> service.execute(cmd()))
        .isInstanceOf(TreasuryWalletAlreadyProvisionedException.class);

    verify(kmsKeyLifecyclePort, never()).updateAlias(ALIAS, KMS_KEY_ID);
    verify(kmsAuditRecorder)
        .record(
            OPERATOR_ID,
            ALIAS,
            KMS_KEY_ID,
            ADDRESS,
            KmsAuditAction.KMS_CREATE_ALIAS,
            false,
            "KmsAliasAlreadyExistsException");
  }

  @Test
  void execute_recordsCreateAliasFailureAndRethrows_whenCreateAliasThrowsOtherError() {
    RuntimeException failure = new IllegalStateException("KMS down");
    doThrow(failure).when(kmsKeyLifecyclePort).createAlias(ALIAS, KMS_KEY_ID);

    assertThatThrownBy(() -> service.execute(cmd())).isSameAs(failure);

    verify(kmsAuditRecorder)
        .record(
            OPERATOR_ID,
            ALIAS,
            KMS_KEY_ID,
            ADDRESS,
            KmsAuditAction.KMS_CREATE_ALIAS,
            false,
            "IllegalStateException");
  }

  @Test
  void execute_recordsUpdateAliasFailure_whenUpdateAliasThrows() {
    doThrow(new KmsAliasAlreadyExistsException("alias taken"))
        .when(kmsKeyLifecyclePort)
        .createAlias(ALIAS, KMS_KEY_ID);
    when(kmsKeyLifecyclePort.describeAliasTarget(ALIAS)).thenReturn(KmsKeyState.PENDING_DELETION);
    RuntimeException updateFailure = new IllegalStateException("KMS update down");
    doThrow(updateFailure).when(kmsKeyLifecyclePort).updateAlias(ALIAS, KMS_KEY_ID);

    assertThatThrownBy(() -> service.execute(cmd())).isSameAs(updateFailure);

    verify(kmsAuditRecorder)
        .record(
            OPERATOR_ID,
            ALIAS,
            KMS_KEY_ID,
            ADDRESS,
            KmsAuditAction.KMS_UPDATE_ALIAS,
            false,
            "IllegalStateException");
  }
}
