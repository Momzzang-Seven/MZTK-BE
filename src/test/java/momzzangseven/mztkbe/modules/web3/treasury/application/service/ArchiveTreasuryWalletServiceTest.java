package momzzangseven.mztkbe.modules.web3.treasury.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import momzzangseven.mztkbe.global.error.treasury.TreasuryWalletStateException;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.ArchiveTreasuryWalletCommand;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.TreasuryWalletView;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.LoadTreasuryWalletPort;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.SaveTreasuryWalletPort;
import momzzangseven.mztkbe.modules.web3.treasury.domain.event.TreasuryWalletArchivedEvent;
import momzzangseven.mztkbe.modules.web3.treasury.domain.model.TreasuryWallet;
import momzzangseven.mztkbe.modules.web3.treasury.domain.vo.TreasuryRole;
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
  private static final String KMS_KEY_ID = "kms-key-1";
  private static final String ADDRESS = "0x" + "a".repeat(40);
  private static final Long OPERATOR_ID = 7L;

  @Mock private LoadTreasuryWalletPort loadTreasuryWalletPort;
  @Mock private SaveTreasuryWalletPort saveTreasuryWalletPort;
  @Mock private TreasuryAuditRecorder treasuryAuditRecorder;
  @Mock private ApplicationEventPublisher applicationEventPublisher;

  private ArchiveTreasuryWalletService service;

  @BeforeEach
  void setUp() {
    Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    service =
        new ArchiveTreasuryWalletService(
            loadTreasuryWalletPort,
            saveTreasuryWalletPort,
            treasuryAuditRecorder,
            applicationEventPublisher,
            clock);
  }

  private TreasuryWallet disabledWallet() {
    Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    return TreasuryWallet.provision(ALIAS, KMS_KEY_ID, ADDRESS, TreasuryRole.REWARD, clock)
        .disable(clock);
  }

  @Test
  void execute_savesArchivedRowAndPublishesEvent() {
    when(loadTreasuryWalletPort.loadByAlias(ALIAS)).thenReturn(Optional.of(disabledWallet()));
    when(saveTreasuryWalletPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

    TreasuryWalletView view = service.execute(new ArchiveTreasuryWalletCommand(ALIAS, OPERATOR_ID));

    assertThat(view.walletAddress()).isEqualTo(ADDRESS);
    ArgumentCaptor<TreasuryWalletArchivedEvent> captor =
        ArgumentCaptor.forClass(TreasuryWalletArchivedEvent.class);
    verify(applicationEventPublisher).publishEvent(captor.capture());
    TreasuryWalletArchivedEvent event = captor.getValue();
    assertThat(event.walletAlias()).isEqualTo(ALIAS);
    assertThat(event.kmsKeyId()).isEqualTo(KMS_KEY_ID);
    assertThat(event.walletAddress()).isEqualTo(ADDRESS);
    assertThat(event.operatorUserId()).isEqualTo(OPERATOR_ID);
    assertThat(event.pendingWindowDays())
        .isEqualTo(ArchiveTreasuryWalletService.DEFAULT_KMS_PENDING_WINDOW_DAYS);
    verify(treasuryAuditRecorder).record(OPERATOR_ID, ADDRESS, true, null);
  }

  @Test
  void execute_throws_andDoesNotPublishEvent_whenWalletMissing() {
    when(loadTreasuryWalletPort.loadByAlias(ALIAS)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.execute(new ArchiveTreasuryWalletCommand(ALIAS, OPERATOR_ID)))
        .isInstanceOf(TreasuryWalletStateException.class);

    verify(applicationEventPublisher, never()).publishEvent(any());
    verify(saveTreasuryWalletPort, never()).save(any());
    verify(treasuryAuditRecorder, never()).record(eq(OPERATOR_ID), any(), eq(true), any());
  }
}
