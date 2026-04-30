package momzzangseven.mztkbe.modules.web3.treasury.infrastructure.event;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import momzzangseven.mztkbe.modules.web3.treasury.application.service.TreasuryAuditRecorder;
import momzzangseven.mztkbe.modules.web3.treasury.domain.event.TreasuryWalletArchivedEvent;
import momzzangseven.mztkbe.modules.web3.treasury.domain.event.TreasuryWalletDisabledEvent;
import momzzangseven.mztkbe.modules.web3.treasury.domain.event.TreasuryWalletProvisionedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TreasuryAuditEventHandlerTest {

  private static final String ALIAS = "reward-treasury";
  private static final String KMS_KEY_ID = "kms-key-1";
  private static final String ADDRESS = "0x" + "a".repeat(40);
  private static final Long OPERATOR_ID = 7L;

  @Mock private TreasuryAuditRecorder treasuryAuditRecorder;

  private TreasuryAuditEventHandler handler;

  @BeforeEach
  void setUp() {
    handler = new TreasuryAuditEventHandler(treasuryAuditRecorder);
  }

  @Test
  void onProvisioned_recordsSuccessAudit() {
    handler.onProvisioned(
        new TreasuryWalletProvisionedEvent(ALIAS, KMS_KEY_ID, ADDRESS, OPERATOR_ID, false));

    verify(treasuryAuditRecorder).record(OPERATOR_ID, ADDRESS, true, null);
    verifyNoMoreInteractions(treasuryAuditRecorder);
  }

  @Test
  void onProvisioned_recordsSuccessAudit_inAliasRepairMode() {
    handler.onProvisioned(
        new TreasuryWalletProvisionedEvent(ALIAS, KMS_KEY_ID, ADDRESS, OPERATOR_ID, true));

    verify(treasuryAuditRecorder).record(OPERATOR_ID, ADDRESS, true, null);
    verifyNoMoreInteractions(treasuryAuditRecorder);
  }

  @Test
  void onDisabled_recordsSuccessAudit() {
    handler.onDisabled(new TreasuryWalletDisabledEvent(ALIAS, KMS_KEY_ID, ADDRESS, OPERATOR_ID));

    verify(treasuryAuditRecorder).record(OPERATOR_ID, ADDRESS, true, null);
    verifyNoMoreInteractions(treasuryAuditRecorder);
  }

  @Test
  void onArchived_recordsSuccessAudit() {
    handler.onArchived(
        new TreasuryWalletArchivedEvent(ALIAS, KMS_KEY_ID, ADDRESS, OPERATOR_ID, 30));

    verify(treasuryAuditRecorder).record(OPERATOR_ID, ADDRESS, true, null);
    verifyNoMoreInteractions(treasuryAuditRecorder);
  }

  @Test
  void onProvisioned_swallowsRecorderException() {
    doThrow(new RuntimeException("audit insert failed"))
        .when(treasuryAuditRecorder)
        .record(anyLong(), any(), anyBoolean(), any());

    assertThatCode(
            () ->
                handler.onProvisioned(
                    new TreasuryWalletProvisionedEvent(
                        ALIAS, KMS_KEY_ID, ADDRESS, OPERATOR_ID, false)))
        .doesNotThrowAnyException();
  }

  @Test
  void onDisabled_swallowsRecorderException() {
    doThrow(new RuntimeException("audit insert failed"))
        .when(treasuryAuditRecorder)
        .record(anyLong(), any(), anyBoolean(), any());

    assertThatCode(
            () ->
                handler.onDisabled(
                    new TreasuryWalletDisabledEvent(ALIAS, KMS_KEY_ID, ADDRESS, OPERATOR_ID)))
        .doesNotThrowAnyException();
  }

  @Test
  void onArchived_swallowsRecorderException() {
    doThrow(new RuntimeException("audit insert failed"))
        .when(treasuryAuditRecorder)
        .record(anyLong(), any(), anyBoolean(), any());

    assertThatCode(
            () ->
                handler.onArchived(
                    new TreasuryWalletArchivedEvent(ALIAS, KMS_KEY_ID, ADDRESS, OPERATOR_ID, 30)))
        .doesNotThrowAnyException();
  }
}
