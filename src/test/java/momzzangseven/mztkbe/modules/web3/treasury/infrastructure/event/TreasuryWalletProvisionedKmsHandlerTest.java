package momzzangseven.mztkbe.modules.web3.treasury.infrastructure.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import momzzangseven.mztkbe.modules.web3.treasury.application.dto.BindKmsAliasCommand;
import momzzangseven.mztkbe.modules.web3.treasury.application.port.in.BindKmsAliasUseCase;
import momzzangseven.mztkbe.modules.web3.treasury.domain.event.KeyLifecycleEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TreasuryWalletProvisionedKmsHandlerTest {

  @Mock private BindKmsAliasUseCase bindKmsAliasUseCase;

  private TreasuryWalletProvisionedKmsHandler handler;

  @BeforeEach
  void setUp() {
    handler = new TreasuryWalletProvisionedKmsHandler(bindKmsAliasUseCase);
  }

  @Test
  void onAliasBound_invokesUseCase_withMappedCommand() {
    KeyLifecycleEvent.BoundAlias event =
        new KeyLifecycleEvent.BoundAlias("kms-key-1", "reward-treasury", "0x" + "a".repeat(40), 7L);

    handler.onAliasBound(event);

    ArgumentCaptor<BindKmsAliasCommand> captor = ArgumentCaptor.forClass(BindKmsAliasCommand.class);
    verify(bindKmsAliasUseCase).execute(captor.capture());
    BindKmsAliasCommand cmd = captor.getValue();
    assertThat(cmd.walletAlias()).isEqualTo("reward-treasury");
    assertThat(cmd.kmsKeyId()).isEqualTo("kms-key-1");
    assertThat(cmd.operatorUserId()).isEqualTo(7L);
  }

  @Test
  void onAliasBound_swallowsUseCaseExceptions() {
    doThrow(new RuntimeException("KMS down")).when(bindKmsAliasUseCase).execute(any());
    KeyLifecycleEvent.BoundAlias event =
        new KeyLifecycleEvent.BoundAlias("kms-key-1", "reward-treasury", null, 7L);

    assertThatCode(() -> handler.onAliasBound(event)).doesNotThrowAnyException();
  }
}
