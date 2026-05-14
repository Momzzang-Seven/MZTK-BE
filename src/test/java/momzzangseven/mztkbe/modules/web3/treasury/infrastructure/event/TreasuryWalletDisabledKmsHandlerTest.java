package momzzangseven.mztkbe.modules.web3.treasury.infrastructure.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import momzzangseven.mztkbe.modules.web3.treasury.application.dto.DisableKmsKeyCommand;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.in.DisableKmsKeyUseCase;
import momzzangseven.mztkbe.modules.web3.treasury.domain.event.KeyLifecycleEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TreasuryWalletDisabledKmsHandlerTest {

  @Mock private DisableKmsKeyUseCase disableKmsKeyUseCase;

  private TreasuryWalletDisabledKmsHandler handler;

  @BeforeEach
  void setUp() {
    handler = new TreasuryWalletDisabledKmsHandler(disableKmsKeyUseCase);
  }

  @Test
  void onKeyDisabled_invokesUseCase_withMappedCommand() {
    KeyLifecycleEvent.Disabled event =
        new KeyLifecycleEvent.Disabled("kms-key-1", "reward-treasury", "0x" + "a".repeat(40), 7L);

    handler.onKeyDisabled(event);

    ArgumentCaptor<DisableKmsKeyCommand> captor =
        ArgumentCaptor.forClass(DisableKmsKeyCommand.class);
    verify(disableKmsKeyUseCase).execute(captor.capture());
    DisableKmsKeyCommand cmd = captor.getValue();
    assertThat(cmd.walletAlias()).isEqualTo("reward-treasury");
    assertThat(cmd.kmsKeyId()).isEqualTo("kms-key-1");
    assertThat(cmd.operatorUserId()).isEqualTo(7L);
  }

  @Test
  void onKeyDisabled_swallowsUseCaseExceptions() {
    doThrow(new RuntimeException("KMS down")).when(disableKmsKeyUseCase).execute(any());
    KeyLifecycleEvent.Disabled event =
        new KeyLifecycleEvent.Disabled("kms-key-1", "reward-treasury", null, 7L);

    assertThatCode(() -> handler.onKeyDisabled(event)).doesNotThrowAnyException();
  }
}
