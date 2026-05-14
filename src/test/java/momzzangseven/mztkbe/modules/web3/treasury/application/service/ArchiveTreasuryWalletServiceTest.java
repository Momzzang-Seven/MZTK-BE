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
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.ArchiveTreasuryWalletCommand;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.TreasuryWalletView;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.LoadTreasuryWalletPort;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.SaveTreasuryWalletPort;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.TreasuryAdvisoryLockPort;
import momzzangseven.mztkbe.modules.web3.treasury.domain.event.AliasArchivedAuditEvent;
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
class ArchiveTreasuryWalletServiceTest {

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

  private ArchiveTreasuryWalletService service;

  @BeforeEach
  void setUp() {
    service =
        new ArchiveTreasuryWalletService(
            loadTreasuryWalletPort,
            saveTreasuryWalletPort,
            treasuryAuditRecorder,
            treasuryAdvisoryLockPort,
            applicationEventPublisher,
            CLOCK);
  }

  private TreasuryWallet disabledWallet(String alias, TreasuryRole role) {
    return TreasuryWallet.provision(alias, KMS_KEY_ID, ADDRESS, role, CLOCK).disable(CLOCK);
  }

  private TreasuryWallet activeWallet(String alias, TreasuryRole role) {
    return TreasuryWallet.provision(alias, KMS_KEY_ID, ADDRESS, role, CLOCK);
  }

  @Test
  void execute_archivesSingleRowCohortAndPublishesEvents() {
    when(loadTreasuryWalletPort.loadByAlias(ALIAS))
        .thenReturn(Optional.of(disabledWallet(ALIAS, TreasuryRole.REWARD)));
    when(loadTreasuryWalletPort.loadAllByTreasuryAddressForUpdate(ADDRESS))
        .thenReturn(List.of(disabledWallet(ALIAS, TreasuryRole.REWARD)));
    when(saveTreasuryWalletPort.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

    TreasuryWalletView view = service.execute(new ArchiveTreasuryWalletCommand(ALIAS, OPERATOR_ID));

    assertThat(view.walletAddress()).isEqualTo(ADDRESS);
    assertThat(view.status()).isEqualTo(TreasuryWalletStatus.ARCHIVED);
    verify(treasuryAdvisoryLockPort).lockForAddress(ADDRESS);

    ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
    verify(applicationEventPublisher, times(2)).publishEvent(eventCaptor.capture());
    assertThat(eventCaptor.getAllValues())
        .anySatisfy(e -> assertThat(e).isInstanceOf(AliasArchivedAuditEvent.class))
        .anySatisfy(
            e -> {
              assertThat(e).isInstanceOf(KeyLifecycleEvent.ScheduledDeletion.class);
              assertThat(((KeyLifecycleEvent.ScheduledDeletion) e).pendingWindowDays())
                  .isEqualTo(ArchiveTreasuryWalletService.DEFAULT_KMS_PENDING_WINDOW_DAYS);
            });
    verify(treasuryAuditRecorder, never()).record(eq(OPERATOR_ID), any(), any(), eq(true), any());
  }

  @Test
  void execute_archivesWholeCohort_andPublishesOneKeyEventForManyAliases() {
    when(loadTreasuryWalletPort.loadByAlias(ALIAS))
        .thenReturn(Optional.of(disabledWallet(ALIAS, TreasuryRole.REWARD)));
    when(loadTreasuryWalletPort.loadAllByTreasuryAddressForUpdate(ADDRESS))
        .thenReturn(
            List.of(
                disabledWallet(ALIAS, TreasuryRole.REWARD),
                disabledWallet(SIBLING_ALIAS, TreasuryRole.SPONSOR)));
    when(saveTreasuryWalletPort.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

    service.execute(new ArchiveTreasuryWalletCommand(ALIAS, OPERATOR_ID));

    ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
    verify(applicationEventPublisher, times(3)).publishEvent(eventCaptor.capture());
    long aliasEvents =
        eventCaptor.getAllValues().stream()
            .filter(AliasArchivedAuditEvent.class::isInstance)
            .count();
    long keyEvents =
        eventCaptor.getAllValues().stream()
            .filter(KeyLifecycleEvent.ScheduledDeletion.class::isInstance)
            .count();
    assertThat(aliasEvents).isEqualTo(2);
    assertThat(keyEvents).isEqualTo(1);
  }

  @Test
  void execute_throws_whenWalletMissing() {
    when(loadTreasuryWalletPort.loadByAlias(ALIAS)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.execute(new ArchiveTreasuryWalletCommand(ALIAS, OPERATOR_ID)))
        .isInstanceOf(TreasuryWalletStateException.class);

    verify(applicationEventPublisher, never()).publishEvent(any());
    verify(saveTreasuryWalletPort, never()).saveAll(any());
  }

  @Test
  void execute_rejectsMixedStateCohort_withInconsistentAudit() {
    TreasuryWallet disabled = disabledWallet(ALIAS, TreasuryRole.REWARD);
    TreasuryWallet activeSibling = activeWallet(SIBLING_ALIAS, TreasuryRole.SPONSOR);
    when(loadTreasuryWalletPort.loadByAlias(ALIAS)).thenReturn(Optional.of(disabled));
    when(loadTreasuryWalletPort.loadAllByTreasuryAddressForUpdate(ADDRESS))
        .thenReturn(List.of(disabled, activeSibling));

    assertThatThrownBy(() -> service.execute(new ArchiveTreasuryWalletCommand(ALIAS, OPERATOR_ID)))
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
        .thenReturn(Optional.of(disabledWallet(ALIAS, TreasuryRole.REWARD)));
    when(loadTreasuryWalletPort.loadAllByTreasuryAddressForUpdate(ADDRESS))
        .thenReturn(List.of(disabledWallet(ALIAS, TreasuryRole.REWARD)));
    when(saveTreasuryWalletPort.saveAll(any())).thenThrow(new RuntimeException("db down"));

    assertThatThrownBy(() -> service.execute(new ArchiveTreasuryWalletCommand(ALIAS, OPERATOR_ID)))
        .isInstanceOf(RuntimeException.class);

    verify(treasuryAuditRecorder)
        .record(anyLong(), eq(ALIAS), eq(ADDRESS), anyBoolean(), eq("RuntimeException"));
  }
}
