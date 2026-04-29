package momzzangseven.mztkbe.modules.web3.treasury.infrastructure.event;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import momzzangseven.mztkbe.modules.web3.treasury.application.dto.DisableKmsKeyCommand;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.in.DisableKmsKeyUseCase;
import momzzangseven.mztkbe.modules.web3.treasury.domain.event.TreasuryWalletDisabledEvent;
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
  void onDisabled_invokesUseCase_withMappedCommand() {
    TreasuryWalletDisabledEvent event =
        new TreasuryWalletDisabledEvent("reward-treasury", "kms-key-1", "0x" + "a".repeat(40), 7L);

    handler.onDisabled(event);

    ArgumentCaptor<DisableKmsKeyCommand> captor = ArgumentCaptor.forClass(DisableKmsKeyCommand.class);
    verify(disableKmsKeyUseCase).execute(captor.capture());
    DisableKmsKeyCommand cmd = captor.getValue();
    org.assertj.core.api.Assertions.assertThat(cmd.walletAlias()).isEqualTo("reward-treasury");
    org.assertj.core.api.Assertions.assertThat(cmd.kmsKeyId()).isEqualTo("kms-key-1");
    org.assertj.core.api.Assertions.assertThat(cmd.operatorUserId()).isEqualTo(7L);
  }

  @Test
  void onDisabled_swallowsUseCaseExceptions() {
    doThrow(new RuntimeException("KMS down")).when(disableKmsKeyUseCase).execute(any());
    TreasuryWalletDisabledEvent event =
        new TreasuryWalletDisabledEvent("reward-treasury", "kms-key-1", null, 7L);

    assertThatCode(() -> handler.onDisabled(event)).doesNotThrowAnyException();
  }
}
