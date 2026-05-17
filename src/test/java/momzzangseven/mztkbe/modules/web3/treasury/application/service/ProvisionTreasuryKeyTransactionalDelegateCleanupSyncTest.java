package momzzangseven.mztkbe.modules.web3.treasury.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicBoolean;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.KmsAuditAction;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.KmsKeyLifecyclePort;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.KmsKeyMaterialWrapperPort;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.LoadTreasuryWalletPort;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.SaveTreasuryWalletPort;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.SignDigestPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionSynchronization;

/**
 * Drives the rollback-cleanup synchronization directly via the package-private {@code
 * buildCleanupSync} factory. Avoids needing a real transaction context and lets the test focus on
 * the spec §9.2 invariants for each {@code afterCompletion} status.
 */
@ExtendWith(MockitoExtension.class)
class ProvisionTreasuryKeyTransactionalDelegateCleanupSyncTest {

  private static final String KMS_KEY_ID = "kms-key-1";
  private static final Long OPERATOR_ID = 7L;
  private static final String DERIVED_ADDRESS = "0x" + "a".repeat(40);
  private static final String WALLET_ALIAS = "reward-treasury";

  @Mock private LoadTreasuryWalletPort loadTreasuryWalletPort;
  @Mock private SaveTreasuryWalletPort saveTreasuryWalletPort;
  @Mock private KmsKeyLifecyclePort kmsKeyLifecyclePort;
  @Mock private KmsKeyMaterialWrapperPort kmsKeyMaterialWrapperPort;
  @Mock private SignDigestPort signDigestPort;
  @Mock private TreasuryAuditRecorder treasuryAuditRecorder;
  @Mock private KmsAuditRecorder kmsAuditRecorder;
  @Mock private ApplicationEventPublisher applicationEventPublisher;

  private ProvisionTreasuryKeyTransactionalDelegate delegate;
  private AtomicBoolean cleanupInvoked;
  private AtomicBoolean failureAuditWritten;

  @BeforeEach
  void setUp() {
    Clock fixed = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    delegate =
        new ProvisionTreasuryKeyTransactionalDelegate(
            loadTreasuryWalletPort,
            saveTreasuryWalletPort,
            kmsKeyLifecyclePort,
            kmsKeyMaterialWrapperPort,
            signDigestPort,
            treasuryAuditRecorder,
            kmsAuditRecorder,
            applicationEventPublisher,
            fixed);
    cleanupInvoked = new AtomicBoolean(false);
    failureAuditWritten = new AtomicBoolean(false);
  }

  @Test
  void afterCompletion_committed_isNoOp() {
    TransactionSynchronization sync = buildSync();

    sync.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);

    verifyNoInteractions(kmsKeyLifecyclePort, treasuryAuditRecorder, kmsAuditRecorder);
    assertThat(cleanupInvoked).isFalse();
    assertThat(failureAuditWritten).isFalse();
  }

  @Test
  void afterCompletion_rolledBack_cleansUpKmsKeyOnce_andMarksCleanupInvoked() {
    TransactionSynchronization sync = buildSync();

    sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);

    verify(kmsKeyLifecyclePort, times(1)).disableKey(KMS_KEY_ID);
    verify(kmsKeyLifecyclePort, times(1)).scheduleKeyDeletion(eq(KMS_KEY_ID), anyInt());
    verifyNoInteractions(treasuryAuditRecorder, kmsAuditRecorder);
    assertThat(cleanupInvoked).isTrue();
    assertThat(failureAuditWritten).isFalse();
  }

  @Test
  void afterCompletion_rolledBack_isIdempotent_whenCleanupAlreadyClaimed() {
    cleanupInvoked.set(true);
    TransactionSynchronization sync = buildSync();

    sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);

    verify(kmsKeyLifecyclePort, never()).disableKey(KMS_KEY_ID);
    verify(kmsKeyLifecyclePort, never()).scheduleKeyDeletion(eq(KMS_KEY_ID), anyInt());
    verifyNoInteractions(kmsAuditRecorder);
  }

  @Test
  void afterCompletion_unknown_writesAudit_marksCleanupInvoked_skipsKmsCalls() {
    TransactionSynchronization sync = buildSync();

    sync.afterCompletion(TransactionSynchronization.STATUS_UNKNOWN);

    verify(treasuryAuditRecorder).record(OPERATOR_ID, DERIVED_ADDRESS, false, "TX_STATUS_UNKNOWN");
    verify(kmsKeyLifecyclePort, never()).disableKey(KMS_KEY_ID);
    verify(kmsKeyLifecyclePort, never()).scheduleKeyDeletion(eq(KMS_KEY_ID), anyInt());
    verifyNoInteractions(kmsAuditRecorder);
    assertThat(cleanupInvoked).isTrue();
    assertThat(failureAuditWritten).isTrue();
  }

  @Test
  void afterCompletion_rolledBack_recordsKmsAudit_whenDisableKeyFails() {
    doThrow(new IllegalStateException("disableKey down"))
        .when(kmsKeyLifecyclePort)
        .disableKey(KMS_KEY_ID);
    TransactionSynchronization sync = buildSync();

    sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);

    verify(kmsAuditRecorder)
        .record(
            OPERATOR_ID,
            WALLET_ALIAS,
            KMS_KEY_ID,
            DERIVED_ADDRESS,
            KmsAuditAction.KMS_DISABLE,
            false,
            "IllegalStateException");
    verify(kmsKeyLifecyclePort, times(1)).scheduleKeyDeletion(eq(KMS_KEY_ID), anyInt());
    assertThat(cleanupInvoked).isTrue();
  }

  @Test
  void afterCompletion_rolledBack_recordsKmsAudit_whenScheduleKeyDeletionFails() {
    doThrow(new IllegalStateException("scheduleKeyDeletion down"))
        .when(kmsKeyLifecyclePort)
        .scheduleKeyDeletion(eq(KMS_KEY_ID), anyInt());
    TransactionSynchronization sync = buildSync();

    sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);

    verify(kmsKeyLifecyclePort, times(1)).disableKey(KMS_KEY_ID);
    verify(kmsAuditRecorder)
        .record(
            OPERATOR_ID,
            WALLET_ALIAS,
            KMS_KEY_ID,
            DERIVED_ADDRESS,
            KmsAuditAction.KMS_SCHEDULE_DELETION,
            false,
            "IllegalStateException");
    assertThat(cleanupInvoked).isTrue();
  }

  @Test
  void afterCompletion_rolledBack_recordsBothKmsAudits_whenBothCleanupCallsFail() {
    doThrow(new IllegalStateException("disableKey down"))
        .when(kmsKeyLifecyclePort)
        .disableKey(KMS_KEY_ID);
    doThrow(new IllegalStateException("scheduleKeyDeletion down"))
        .when(kmsKeyLifecyclePort)
        .scheduleKeyDeletion(eq(KMS_KEY_ID), anyInt());
    TransactionSynchronization sync = buildSync();

    sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);

    verify(kmsAuditRecorder)
        .record(
            OPERATOR_ID,
            WALLET_ALIAS,
            KMS_KEY_ID,
            DERIVED_ADDRESS,
            KmsAuditAction.KMS_DISABLE,
            false,
            "IllegalStateException");
    verify(kmsAuditRecorder)
        .record(
            OPERATOR_ID,
            WALLET_ALIAS,
            KMS_KEY_ID,
            DERIVED_ADDRESS,
            KmsAuditAction.KMS_SCHEDULE_DELETION,
            false,
            "IllegalStateException");
    assertThat(cleanupInvoked).isTrue();
  }

  private TransactionSynchronization buildSync() {
    return delegate.buildCleanupSync(
        KMS_KEY_ID,
        cleanupInvoked,
        failureAuditWritten,
        OPERATOR_ID,
        WALLET_ALIAS,
        DERIVED_ADDRESS);
  }
}
