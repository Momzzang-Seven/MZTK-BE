package momzzangseven.mztkbe.modules.web3.wallet.infrastructure.external.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import momzzangseven.mztkbe.global.error.wallet.WalletApprovalUnavailableException;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.CancelExecutionIntentCommand;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.CancelExecutionIntentUseCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WalletApprovalExecutionCancelAdapterTest {

  @Mock private CancelExecutionIntentUseCase cancelExecutionIntentUseCase;

  @Test
  void cancelIfSignable_delegatesToExecutionUseCase() {
    when(cancelExecutionIntentUseCase.cancelIfSignable(any())).thenReturn(true);
    WalletApprovalExecutionCancelAdapter adapter =
        new WalletApprovalExecutionCancelAdapter(Optional.of(cancelExecutionIntentUseCase));

    boolean canceled = adapter.cancelIfSignable("intent-1", "CODE", "reason");

    ArgumentCaptor<CancelExecutionIntentCommand> captor =
        ArgumentCaptor.forClass(CancelExecutionIntentCommand.class);
    verify(cancelExecutionIntentUseCase).cancelIfSignable(captor.capture());
    assertThat(canceled).isTrue();
    assertThat(captor.getValue().executionIntentId()).isEqualTo("intent-1");
    assertThat(captor.getValue().errorCode()).isEqualTo("CODE");
  }

  @Test
  void cancelIfSignable_whenUseCaseMissing_failsFast() {
    WalletApprovalExecutionCancelAdapter adapter =
        new WalletApprovalExecutionCancelAdapter(Optional.empty());

    assertThatThrownBy(() -> adapter.cancelIfSignable("intent-1", "CODE", "reason"))
        .isInstanceOf(WalletApprovalUnavailableException.class);
  }
}
