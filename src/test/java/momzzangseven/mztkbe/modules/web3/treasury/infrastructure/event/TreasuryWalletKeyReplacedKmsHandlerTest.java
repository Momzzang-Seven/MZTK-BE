package momzzangseven.mztkbe.modules.web3.treasury.infrastructure.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import momzzangseven.mztkbe.modules.web3.treasury.application.dto.ReplaceKmsKeyCommand;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.in.ReplaceKmsKeyUseCase;
import momzzangseven.mztkbe.modules.web3.treasury.domain.event.TreasuryWalletKeyReplacedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Verifies the thin AFTER_COMMIT handler builds the command correctly and never propagates. */
@ExtendWith(MockitoExtension.class)
class TreasuryWalletKeyReplacedKmsHandlerTest {

  @Mock private ReplaceKmsKeyUseCase replaceKmsKeyUseCase;

  @InjectMocks private TreasuryWalletKeyReplacedKmsHandler handler;

  @Test
  void delegatesToUseCase_withCommandMatchingEvent() {
    TreasuryWalletKeyReplacedEvent event =
        new TreasuryWalletKeyReplacedEvent(
            "reward-treasury", "old-kms", "new-kms", "0x" + "a".repeat(40), 1L, true);

    handler.on(event);

    ArgumentCaptor<ReplaceKmsKeyCommand> captor =
        ArgumentCaptor.forClass(ReplaceKmsKeyCommand.class);
    verify(replaceKmsKeyUseCase).execute(captor.capture());
    ReplaceKmsKeyCommand cmd = captor.getValue();
    assertThat(cmd.walletAlias()).isEqualTo("reward-treasury");
    assertThat(cmd.oldKmsKeyId()).isEqualTo("old-kms");
    assertThat(cmd.newKmsKeyId()).isEqualTo("new-kms");
    assertThat(cmd.walletAddress()).isEqualTo(event.walletAddress());
    assertThat(cmd.operatorUserId()).isEqualTo(1L);
    assertThat(cmd.disposeOldKey()).isTrue();
  }

  @Test
  void useCaseFailure_doesNotPropagate() {
    TreasuryWalletKeyReplacedEvent event =
        new TreasuryWalletKeyReplacedEvent(
            "reward-treasury", "old-kms", "new-kms", "0x" + "a".repeat(40), 1L, true);
    doThrow(new RuntimeException("boom")).when(replaceKmsKeyUseCase).execute(any());

    handler.on(event);
  }
}
