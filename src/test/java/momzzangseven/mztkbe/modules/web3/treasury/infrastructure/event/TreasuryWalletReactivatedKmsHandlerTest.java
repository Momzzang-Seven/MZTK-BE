package momzzangseven.mztkbe.modules.web3.treasury.infrastructure.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import momzzangseven.mztkbe.modules.web3.treasury.application.dto.EnableKmsKeyCommand;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.in.EnableKmsKeyUseCase;
import momzzangseven.mztkbe.modules.web3.treasury.domain.event.TreasuryWalletReactivatedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Verifies the thin AFTER_COMMIT handler builds the command correctly and never propagates. */
@ExtendWith(MockitoExtension.class)
class TreasuryWalletReactivatedKmsHandlerTest {

  @Mock private EnableKmsKeyUseCase enableKmsKeyUseCase;

  @InjectMocks private TreasuryWalletReactivatedKmsHandler handler;

  @Test
  void delegatesToUseCase_withCommandMatchingEvent() {
    TreasuryWalletReactivatedEvent event =
        new TreasuryWalletReactivatedEvent("reward-treasury", "kms-id", "0x" + "a".repeat(40), 1L);

    handler.on(event);

    ArgumentCaptor<EnableKmsKeyCommand> captor = ArgumentCaptor.forClass(EnableKmsKeyCommand.class);
    verify(enableKmsKeyUseCase).execute(captor.capture());
    EnableKmsKeyCommand cmd = captor.getValue();
    assertThat(cmd.walletAlias()).isEqualTo("reward-treasury");
    assertThat(cmd.kmsKeyId()).isEqualTo("kms-id");
    assertThat(cmd.walletAddress()).isEqualTo(event.walletAddress());
    assertThat(cmd.operatorUserId()).isEqualTo(1L);
  }

  @Test
  void useCaseFailure_doesNotPropagate() {
    TreasuryWalletReactivatedEvent event =
        new TreasuryWalletReactivatedEvent("reward-treasury", "kms-id", "0x" + "a".repeat(40), 1L);
    doThrow(new RuntimeException("boom")).when(enableKmsKeyUseCase).execute(any());

    handler.on(event);
  }
}
