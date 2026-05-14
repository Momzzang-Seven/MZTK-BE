package momzzangseven.mztkbe.modules.web3.treasury.infrastructure.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import momzzangseven.mztkbe.modules.web3.treasury.application.dto.ScheduleKmsKeyDeletionCommand;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.in.ScheduleKmsKeyDeletionUseCase;
import momzzangseven.mztkbe.modules.web3.treasury.domain.event.KeyLifecycleEvent;
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
  void onKeyScheduledDeletion_invokesUseCase_withMappedCommand() {
    KeyLifecycleEvent.ScheduledDeletion event =
        new KeyLifecycleEvent.ScheduledDeletion(
            "kms-key-1", "reward-treasury", "0x" + "a".repeat(40), 7L, 30);

    handler.onKeyScheduledDeletion(event);

    ArgumentCaptor<ScheduleKmsKeyDeletionCommand> captor =
        ArgumentCaptor.forClass(ScheduleKmsKeyDeletionCommand.class);
    verify(scheduleKmsKeyDeletionUseCase).execute(captor.capture());
    ScheduleKmsKeyDeletionCommand cmd = captor.getValue();
    assertThat(cmd.walletAlias()).isEqualTo("reward-treasury");
    assertThat(cmd.kmsKeyId()).isEqualTo("kms-key-1");
    assertThat(cmd.pendingWindowDays()).isEqualTo(30);
  }

  @Test
  void onKeyScheduledDeletion_swallowsUseCaseExceptions() {
    doThrow(new RuntimeException("KMS down")).when(scheduleKmsKeyDeletionUseCase).execute(any());
    KeyLifecycleEvent.ScheduledDeletion event =
        new KeyLifecycleEvent.ScheduledDeletion("kms-key-1", "reward-treasury", null, 7L, 30);

    assertThatCode(() -> handler.onKeyScheduledDeletion(event)).doesNotThrowAnyException();
  }
}
