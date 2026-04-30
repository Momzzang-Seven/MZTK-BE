package momzzangseven.mztkbe.modules.web3.treasury.infrastructure.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import momzzangseven.mztkbe.modules.web3.treasury.application.dto.ScheduleKmsKeyDeletionCommand;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.in.ScheduleKmsKeyDeletionUseCase;
import momzzangseven.mztkbe.modules.web3.treasury.domain.event.TreasuryWalletArchivedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TreasuryWalletArchivedKmsHandlerTest {

  @Mock private ScheduleKmsKeyDeletionUseCase scheduleKmsKeyDeletionUseCase;

  private TreasuryWalletArchivedKmsHandler handler;

  @BeforeEach
  void setUp() {
    handler = new TreasuryWalletArchivedKmsHandler(scheduleKmsKeyDeletionUseCase);
  }

  @Test
  void onArchived_invokesUseCase_withMappedCommand() {
    TreasuryWalletArchivedEvent event =
        new TreasuryWalletArchivedEvent(
            "reward-treasury", "kms-key-1", "0x" + "a".repeat(40), 7L, 30);

    handler.onArchived(event);

    ArgumentCaptor<ScheduleKmsKeyDeletionCommand> captor =
        ArgumentCaptor.forClass(ScheduleKmsKeyDeletionCommand.class);
    verify(scheduleKmsKeyDeletionUseCase).execute(captor.capture());
    ScheduleKmsKeyDeletionCommand cmd = captor.getValue();
    assertThat(cmd.walletAlias()).isEqualTo("reward-treasury");
    assertThat(cmd.kmsKeyId()).isEqualTo("kms-key-1");
    assertThat(cmd.pendingWindowDays()).isEqualTo(30);
  }

  @Test
  void onArchived_swallowsUseCaseExceptions() {
    doThrow(new RuntimeException("KMS down")).when(scheduleKmsKeyDeletionUseCase).execute(any());
    TreasuryWalletArchivedEvent event =
        new TreasuryWalletArchivedEvent("reward-treasury", "kms-key-1", null, 7L, 30);

    assertThatCode(() -> handler.onArchived(event)).doesNotThrowAnyException();
  }
}
