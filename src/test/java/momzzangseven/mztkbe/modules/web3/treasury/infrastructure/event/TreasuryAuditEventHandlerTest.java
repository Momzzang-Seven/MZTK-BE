package momzzangseven.mztkbe.modules.web3.treasury.infrastructure.event;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import momzzangseven.mztkbe.modules.web3.treasury.application.port.in.RecordTreasuryAuditUseCase;
import momzzangseven.mztkbe.modules.web3.treasury.domain.event.AliasArchivedAuditEvent;
import momzzangseven.mztkbe.modules.web3.treasury.domain.event.AliasDisabledAuditEvent;
import momzzangseven.mztkbe.modules.web3.treasury.domain.event.AliasProvisionedAuditEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TreasuryAuditEventHandlerTest {

  private static final String ALIAS = "reward-treasury";
  private static final String ADDRESS = "0x" + "a".repeat(40);
  private static final Long OPERATOR_ID = 7L;

  @Mock private RecordTreasuryAuditUseCase recordTreasuryAuditUseCase;

  private TreasuryAuditEventHandler handler;

  @BeforeEach
  void setUp() {
    handler = new TreasuryAuditEventHandler(recordTreasuryAuditUseCase);
  }

  @Test
  void onProvisioned_recordsSuccessAudit() {
    handler.onProvisioned(
        new AliasProvisionedAuditEvent(ALIAS, ADDRESS, OPERATOR_ID, false, false));

    verify(recordTreasuryAuditUseCase).record(OPERATOR_ID, ALIAS, ADDRESS, true, null);
    verifyNoMoreInteractions(recordTreasuryAuditUseCase);
  }

  @Test
  void onProvisioned_recordsSuccessAudit_inCoBindMode() {
    handler.onProvisioned(new AliasProvisionedAuditEvent(ALIAS, ADDRESS, OPERATOR_ID, true, false));

    verify(recordTreasuryAuditUseCase).record(OPERATOR_ID, ALIAS, ADDRESS, true, null);
    verifyNoMoreInteractions(recordTreasuryAuditUseCase);
  }

  @Test
  void onProvisioned_recordsSuccessAudit_inAliasRepairMode() {
    handler.onProvisioned(new AliasProvisionedAuditEvent(ALIAS, ADDRESS, OPERATOR_ID, false, true));

    verify(recordTreasuryAuditUseCase).record(OPERATOR_ID, ALIAS, ADDRESS, true, null);
    verifyNoMoreInteractions(recordTreasuryAuditUseCase);
  }

  @Test
  void onDisabled_recordsSuccessAudit() {
    handler.onDisabled(new AliasDisabledAuditEvent(ALIAS, ADDRESS, OPERATOR_ID));

    verify(recordTreasuryAuditUseCase).record(OPERATOR_ID, ALIAS, ADDRESS, true, null);
    verifyNoMoreInteractions(recordTreasuryAuditUseCase);
  }

  @Test
  void onArchived_recordsSuccessAudit() {
    handler.onArchived(new AliasArchivedAuditEvent(ALIAS, ADDRESS, OPERATOR_ID));

    verify(recordTreasuryAuditUseCase).record(OPERATOR_ID, ALIAS, ADDRESS, true, null);
    verifyNoMoreInteractions(recordTreasuryAuditUseCase);
  }

  @Test
  void onProvisioned_swallowsRecorderException() {
    doThrow(new RuntimeException("audit insert failed"))
        .when(recordTreasuryAuditUseCase)
        .record(anyLong(), any(), any(), anyBoolean(), any());

    assertThatCode(
            () ->
                handler.onProvisioned(
                    new AliasProvisionedAuditEvent(ALIAS, ADDRESS, OPERATOR_ID, false, false)))
        .doesNotThrowAnyException();
  }

  @Test
  void onDisabled_swallowsRecorderException() {
    doThrow(new RuntimeException("audit insert failed"))
        .when(recordTreasuryAuditUseCase)
        .record(anyLong(), any(), any(), anyBoolean(), any());

    assertThatCode(
            () -> handler.onDisabled(new AliasDisabledAuditEvent(ALIAS, ADDRESS, OPERATOR_ID)))
        .doesNotThrowAnyException();
  }

  @Test
  void onArchived_swallowsRecorderException() {
    doThrow(new RuntimeException("audit insert failed"))
        .when(recordTreasuryAuditUseCase)
        .record(anyLong(), any(), any(), anyBoolean(), any());

    assertThatCode(
            () -> handler.onArchived(new AliasArchivedAuditEvent(ALIAS, ADDRESS, OPERATOR_ID)))
        .doesNotThrowAnyException();
  }
}
