package momzzangseven.mztkbe.modules.web3.treasury.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.EnableKmsKeyCommand;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.KmsAuditAction;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.KmsKeyLifecyclePort;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.LoadTreasuryWalletPort;
import momzzangseven.mztkbe.modules.web3.treasury.domain.model.TreasuryWallet;
import momzzangseven.mztkbe.modules.web3.treasury.domain.vo.TreasuryWalletStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EnableKmsKeyServiceTest {

  private static final String ALIAS = "reward-treasury";
  private static final String KMS_KEY_ID = "kms-key-1";
  private static final String ADDRESS = "0x" + "a".repeat(40);
  private static final Long OPERATOR_ID = 7L;

  @Mock private KmsKeyLifecyclePort kmsKeyLifecyclePort;
  @Mock private KmsAuditRecorder kmsAuditRecorder;
  @Mock private LoadTreasuryWalletPort loadTreasuryWalletPort;

  private EnableKmsKeyService service;

  @BeforeEach
  void setUp() {
    service =
        new EnableKmsKeyService(kmsKeyLifecyclePort, kmsAuditRecorder, loadTreasuryWalletPort);
  }

  private TreasuryWallet wallet(String kmsKeyId, TreasuryWalletStatus status) {
    return TreasuryWallet.builder()
        .walletAlias(ALIAS)
        .kmsKeyId(kmsKeyId)
        .walletAddress(ADDRESS)
        .status(status)
        .build();
  }

  @Test
  void execute_recordsSuccess_whenKmsCallSucceeds() {
    when(loadTreasuryWalletPort.loadByAliasForUpdate(ALIAS))
        .thenReturn(Optional.of(wallet(KMS_KEY_ID, TreasuryWalletStatus.ACTIVE)));

    service.execute(new EnableKmsKeyCommand(ALIAS, KMS_KEY_ID, ADDRESS, OPERATOR_ID));

    verify(kmsKeyLifecyclePort).enableKey(KMS_KEY_ID);
    verify(kmsAuditRecorder)
        .record(OPERATOR_ID, ALIAS, KMS_KEY_ID, ADDRESS, KmsAuditAction.KMS_ENABLE, true, null);
    verifyNoMoreInteractions(kmsAuditRecorder);
  }

  @Test
  void execute_recordsFailureAndRethrows_whenKmsCallThrows() {
    when(loadTreasuryWalletPort.loadByAliasForUpdate(ALIAS))
        .thenReturn(Optional.of(wallet(KMS_KEY_ID, TreasuryWalletStatus.ACTIVE)));
    RuntimeException kmsFailure = new IllegalStateException("KMS down");
    doThrow(kmsFailure).when(kmsKeyLifecyclePort).enableKey(KMS_KEY_ID);

    assertThatThrownBy(
            () -> service.execute(new EnableKmsKeyCommand(ALIAS, KMS_KEY_ID, ADDRESS, OPERATOR_ID)))
        .isSameAs(kmsFailure);

    verify(kmsAuditRecorder)
        .record(
            OPERATOR_ID,
            ALIAS,
            KMS_KEY_ID,
            ADDRESS,
            KmsAuditAction.KMS_ENABLE,
            false,
            "IllegalStateException");
  }

  @Test
  void staleSkip_rowMissing_recordsAudit_skipsKms() {
    when(loadTreasuryWalletPort.loadByAliasForUpdate(ALIAS)).thenReturn(Optional.empty());

    service.execute(new EnableKmsKeyCommand(ALIAS, KMS_KEY_ID, ADDRESS, OPERATOR_ID));

    verify(kmsAuditRecorder)
        .record(
            OPERATOR_ID,
            ALIAS,
            KMS_KEY_ID,
            ADDRESS,
            KmsAuditAction.KMS_ENABLE_SKIPPED,
            true,
            "ROW_MISSING");
    verify(kmsKeyLifecyclePort, never()).enableKey(anyString());
    verifyNoMoreInteractions(kmsAuditRecorder);
  }

  @Test
  void staleSkip_keyIdMismatch_recordsAudit_skipsKms() {
    when(loadTreasuryWalletPort.loadByAliasForUpdate(ALIAS))
        .thenReturn(Optional.of(wallet("DIFFERENT_KEY", TreasuryWalletStatus.ACTIVE)));

    service.execute(new EnableKmsKeyCommand(ALIAS, KMS_KEY_ID, ADDRESS, OPERATOR_ID));

    verify(kmsAuditRecorder)
        .record(
            OPERATOR_ID,
            ALIAS,
            KMS_KEY_ID,
            ADDRESS,
            KmsAuditAction.KMS_ENABLE_SKIPPED,
            true,
            "KEY_ID_MISMATCH");
    verify(kmsKeyLifecyclePort, never()).enableKey(anyString());
    verifyNoMoreInteractions(kmsAuditRecorder);
  }

  @Test
  void staleSkip_currentKeyNull_recordsAuditAsKeyIdMismatch() {
    when(loadTreasuryWalletPort.loadByAliasForUpdate(ALIAS))
        .thenReturn(Optional.of(wallet(null, TreasuryWalletStatus.ACTIVE)));

    service.execute(new EnableKmsKeyCommand(ALIAS, KMS_KEY_ID, ADDRESS, OPERATOR_ID));

    verify(kmsAuditRecorder)
        .record(
            OPERATOR_ID,
            ALIAS,
            KMS_KEY_ID,
            ADDRESS,
            KmsAuditAction.KMS_ENABLE_SKIPPED,
            true,
            "KEY_ID_MISMATCH");
    verify(kmsKeyLifecyclePort, never()).enableKey(anyString());
    verifyNoMoreInteractions(kmsAuditRecorder);
  }

  @Test
  void staleSkip_statusMismatch_recordsAudit_skipsKms() {
    when(loadTreasuryWalletPort.loadByAliasForUpdate(ALIAS))
        .thenReturn(Optional.of(wallet(KMS_KEY_ID, TreasuryWalletStatus.DISABLED)));

    service.execute(new EnableKmsKeyCommand(ALIAS, KMS_KEY_ID, ADDRESS, OPERATOR_ID));

    verify(kmsAuditRecorder)
        .record(
            OPERATOR_ID,
            ALIAS,
            KMS_KEY_ID,
            ADDRESS,
            KmsAuditAction.KMS_ENABLE_SKIPPED,
            true,
            "STATUS_MISMATCH");
    verify(kmsKeyLifecyclePort, never()).enableKey(anyString());
    verifyNoMoreInteractions(kmsAuditRecorder);
  }
}
