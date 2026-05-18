package momzzangseven.mztkbe.modules.web3.wallet.infrastructure.external.execution;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

import java.util.Optional;
import momzzangseven.mztkbe.global.error.wallet.WalletApprovalUnavailableException;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.MarkExecutionIntentSucceededUseCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WalletApprovalExecutionSuccessSyncAdapterTest {

  @Mock private MarkExecutionIntentSucceededUseCase markSucceededUseCase;

  @Test
  void syncSucceededTransaction_delegatesToExecutionUseCase() {
    WalletApprovalExecutionSuccessSyncAdapter adapter =
        new WalletApprovalExecutionSuccessSyncAdapter(Optional.of(markSucceededUseCase));

    adapter.syncSucceededTransaction(10L);

    verify(markSucceededUseCase).execute(10L);
  }

  @Test
  void syncSucceededTransaction_whenUseCaseMissing_failsFast() {
    WalletApprovalExecutionSuccessSyncAdapter adapter =
        new WalletApprovalExecutionSuccessSyncAdapter(Optional.empty());

    assertThatThrownBy(() -> adapter.syncSucceededTransaction(10L))
        .isInstanceOf(WalletApprovalUnavailableException.class);
  }
}
