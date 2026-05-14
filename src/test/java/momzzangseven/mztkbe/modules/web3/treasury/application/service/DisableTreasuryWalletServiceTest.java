package momzzangseven.mztkbe.modules.web3.treasury.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import momzzangseven.mztkbe.global.error.treasury.TreasuryWalletStateException;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.DisableTreasuryWalletCommand;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.TreasuryWalletView;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.LoadTreasuryWalletPort;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.SaveTreasuryWalletPort;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.TreasuryAdvisoryLockPort;
import momzzangseven.mztkbe.modules.web3.treasury.domain.event.AliasDisabledAuditEvent;
import momzzangseven.mztkbe.modules.web3.treasury.domain.event.KeyLifecycleEvent;
import momzzangseven.mztkbe.modules.web3.treasury.domain.model.TreasuryWallet;
import momzzangseven.mztkbe.modules.web3.treasury.domain.vo.TreasuryRole;
import momzzangseven.mztkbe.modules.web3.treasury.domain.vo.TreasuryWalletStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class DisableTreasuryWalletServiceTest {

  private static final String ALIAS = TreasuryRole.REWARD.toAlias();
  private static final String SIBLING_ALIAS = TreasuryRole.SPONSOR.toAlias();
  private static final String KMS_KEY_ID = "kms-key-1";
  private static final String ADDRESS = "0x" + "a".repeat(40);
  private static final Long OPERATOR_ID = 7L;
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

  @Mock private LoadTreasuryWalletPort loadTreasuryWalletPort;
  @Mock private SaveTreasuryWalletPort saveTreasuryWalletPort;
  @Mock private TreasuryAuditRecorder treasuryAuditRecorder;
  @Mock private TreasuryAdvisoryLockPort treasuryAdvisoryLockPort;
  @Mock private ApplicationEventPublisher applicationEventPublisher;

  private DisableTreasuryWalletService service;

  @BeforeEach
  void setUp() {
    service =
        new DisableTreasuryWalletService(
            loadTreasuryWalletPort,
            saveTreasuryWalletPort,
            treasuryAuditRecorder,
            treasuryAdvisoryLockPort,
            applicationEventPublisher,
            CLOCK);
  }

  private TreasuryWallet activeWallet(String alias, TreasuryRole role) {
    return TreasuryWallet.provision(alias, KMS_KEY_ID, ADDRESS, role, CLOCK);
  }

  @Test
  void execute_disablesSingleRowCohortAndPublishesEvents() {
    when(loadTreasuryWalletPort.loadByAlias(ALIAS))
        .thenReturn(Optional.of(activeWallet(ALIAS, TreasuryRole.REWARD)));
    when(loadTreasuryWalletPort.loadAllByTreasuryAddressForUpdate(ADDRESS))
        .thenReturn(List.of(activeWallet(ALIAS, TreasuryRole.REWARD)));
    when(saveTreasuryWalletPort.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

    TreasuryWalletView view = service.execute(new DisableTreasuryWalletCommand(ALIAS, OPERATOR_ID));

    assertThat(view.walletAddress()).isEqualTo(ADDRESS);
    assertThat(view.status()).isEqualTo(TreasuryWalletStatus.DISABLED);
    verify(treasuryAdvisoryLockPort).lockForAddress(ADDRESS);

    ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
    verify(applicationEventPublisher, times(2)).publishEvent(eventCaptor.capture());
    assertThat(eventCaptor.getAllValues())
        .anySatisfy(e -> assertThat(e).isInstanceOf(AliasDisabledAuditEvent.class))
        .anySatisfy(e -> assertThat(e).isInstanceOf(KeyLifecycleEvent.Disabled.class));
    verify(treasuryAuditRecorder, never()).record(eq(OPERATOR_ID), any(), any(), eq(true), any());
  }

  @Test
  void execute_disablesWholeCohort_andPublishesOneKeyEventForManyAliases() {
    when(loadTreasuryWalletPort.loadByAlias(ALIAS))
        .thenReturn(Optional.of(activeWallet(ALIAS, TreasuryRole.REWARD)));
    when(loadTreasuryWalletPort.loadAllByTreasuryAddressForUpdate(ADDRESS))
        .thenReturn(
            List.of(
                activeWallet(ALIAS, TreasuryRole.REWARD),
                activeWallet(SIBLING_ALIAS, TreasuryRole.SPONSOR)));
    when(saveTreasuryWalletPort.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

    service.execute(new DisableTreasuryWalletCommand(ALIAS, OPERATOR_ID));

    ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
    verify(applicationEventPublisher, times(3)).publishEvent(eventCaptor.capture());
    long aliasEvents =
        eventCaptor.getAllValues().stream()
            .filter(AliasDisabledAuditEvent.class::isInstance)
            .count();
    long keyEvents =
        eventCaptor.getAllValues().stream()
            .filter(KeyLifecycleEvent.Disabled.class::isInstance)
            .count();
    assertThat(aliasEvents).isEqualTo(2);
    assertThat(keyEvents).isEqualTo(1);
  }

  @Test
  void execute_throws_whenWalletMissing() {
    when(loadTreasuryWalletPort.loadByAlias(ALIAS)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.execute(new DisableTreasuryWalletCommand(ALIAS, OPERATOR_ID)))
        .isInstanceOf(TreasuryWalletStateException.class);

    verify(applicationEventPublisher, never()).publishEvent(any());
    verify(saveTreasuryWalletPort, never()).saveAll(any());
  }

  @Test
  void execute_rejectsMixedStateCohort_withInconsistentAudit() {
    TreasuryWallet active = activeWallet(ALIAS, TreasuryRole.REWARD);
    TreasuryWallet disabledSibling =
        activeWallet(SIBLING_ALIAS, TreasuryRole.SPONSOR).disable(CLOCK);
    when(loadTreasuryWalletPort.loadByAlias(ALIAS)).thenReturn(Optional.of(active));
    when(loadTreasuryWalletPort.loadAllByTreasuryAddressForUpdate(ADDRESS))
        .thenReturn(List.of(active, disabledSibling));

    assertThatThrownBy(() -> service.execute(new DisableTreasuryWalletCommand(ALIAS, OPERATOR_ID)))
        .isInstanceOf(TreasuryWalletStateException.class);

    verify(treasuryAuditRecorder)
        .record(
            eq(OPERATOR_ID), eq(ALIAS), eq(ADDRESS), eq(false), eq("COHORT_STATE_INCONSISTENT"));
    verify(saveTreasuryWalletPort, never()).saveAll(any());
    verify(applicationEventPublisher, never()).publishEvent(any());
  }

  @Test
  void execute_rejectsEmptyCohort_withInconsistentAudit() {
    when(loadTreasuryWalletPort.loadByAlias(ALIAS))
        .thenReturn(Optional.of(activeWallet(ALIAS, TreasuryRole.REWARD)));
    when(loadTreasuryWalletPort.loadAllByTreasuryAddressForUpdate(ADDRESS)).thenReturn(List.of());

    assertThatThrownBy(() -> service.execute(new DisableTreasuryWalletCommand(ALIAS, OPERATOR_ID)))
        .isInstanceOf(TreasuryWalletStateException.class);

    verify(treasuryAuditRecorder)
        .record(
            eq(OPERATOR_ID), eq(ALIAS), eq(ADDRESS), eq(false), eq("COHORT_STATE_INCONSISTENT"));
    verify(saveTreasuryWalletPort, never()).saveAll(any());
    verify(applicationEventPublisher, never()).publishEvent(any());
  }

  @Test
  void execute_recordsFailureAudit_whenSaveAllThrows() {
    when(loadTreasuryWalletPort.loadByAlias(ALIAS))
        .thenReturn(Optional.of(activeWallet(ALIAS, TreasuryRole.REWARD)));
    when(loadTreasuryWalletPort.loadAllByTreasuryAddressForUpdate(ADDRESS))
        .thenReturn(List.of(activeWallet(ALIAS, TreasuryRole.REWARD)));
    when(saveTreasuryWalletPort.saveAll(any())).thenThrow(new RuntimeException("db down"));

    assertThatThrownBy(() -> service.execute(new DisableTreasuryWalletCommand(ALIAS, OPERATOR_ID)))
        .isInstanceOf(RuntimeException.class);

    verify(treasuryAuditRecorder)
        .record(anyLong(), eq(ALIAS), eq(ADDRESS), anyBoolean(), eq("RuntimeException"));
  }
}
