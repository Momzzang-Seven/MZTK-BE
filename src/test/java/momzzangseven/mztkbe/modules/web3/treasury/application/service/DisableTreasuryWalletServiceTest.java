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
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.DisableTreasuryWalletCommand;
import momzzangseven.mztkbe.modules.web3.treasury.application.dto.TreasuryWalletView;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.LoadTreasuryWalletPort;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.out.SaveTreasuryWalletPort;
import momzzangseven.mztkbe.modules.web3.treasury.domain.event.TreasuryWalletDisabledEvent;
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
class DisableTreasuryWalletServiceTest {

  private static final String ALIAS = TreasuryRole.REWARD.toAlias();
  private static final String KMS_KEY_ID = "kms-key-1";
  private static final String ADDRESS = "0x" + "a".repeat(40);
  private static final Long OPERATOR_ID = 7L;

  @Mock private LoadTreasuryWalletPort loadTreasuryWalletPort;
  @Mock private SaveTreasuryWalletPort saveTreasuryWalletPort;
  @Mock private TreasuryAuditRecorder treasuryAuditRecorder;
  @Mock private ApplicationEventPublisher applicationEventPublisher;

  private DisableTreasuryWalletService service;

  @BeforeEach
  void setUp() {
    Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    service =
        new DisableTreasuryWalletService(
            loadTreasuryWalletPort,
            saveTreasuryWalletPort,
            treasuryAuditRecorder,
            applicationEventPublisher,
            clock);
  }

  private TreasuryWallet activeWallet() {
    return TreasuryWallet.provision(
        ALIAS,
        KMS_KEY_ID,
        ADDRESS,
        TreasuryRole.REWARD,
        Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
  }

  @Test
  void execute_savesDisabledRowAndPublishesEvent() {
    when(loadTreasuryWalletPort.loadByAlias(ALIAS)).thenReturn(Optional.of(activeWallet()));
    when(saveTreasuryWalletPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

    TreasuryWalletView view = service.execute(new DisableTreasuryWalletCommand(ALIAS, OPERATOR_ID));

    assertThat(view.walletAddress()).isEqualTo(ADDRESS);
    ArgumentCaptor<TreasuryWalletDisabledEvent> eventCaptor =
        ArgumentCaptor.forClass(TreasuryWalletDisabledEvent.class);
    verify(applicationEventPublisher).publishEvent(eventCaptor.capture());
    TreasuryWalletDisabledEvent event = eventCaptor.getValue();
    assertThat(event.walletAlias()).isEqualTo(ALIAS);
    assertThat(event.kmsKeyId()).isEqualTo(KMS_KEY_ID);
    assertThat(event.walletAddress()).isEqualTo(ADDRESS);
    assertThat(event.operatorUserId()).isEqualTo(OPERATOR_ID);
    verify(treasuryAuditRecorder, never()).record(eq(OPERATOR_ID), any(), eq(true), any());
  }

  @Test
  void execute_throws_andRecordsFailureAudit_whenWalletMissing() {
    when(loadTreasuryWalletPort.loadByAlias(ALIAS)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.execute(new DisableTreasuryWalletCommand(ALIAS, OPERATOR_ID)))
        .isInstanceOf(TreasuryWalletStateException.class);

    verify(applicationEventPublisher, never()).publishEvent(any());
    verify(saveTreasuryWalletPort, never()).save(any());
    verify(treasuryAuditRecorder, never()).record(eq(OPERATOR_ID), any(), eq(true), any());
  }
}
