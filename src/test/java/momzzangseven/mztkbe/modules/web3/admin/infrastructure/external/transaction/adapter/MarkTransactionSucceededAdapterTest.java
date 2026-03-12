package momzzangseven.mztkbe.modules.web3.admin.infrastructure.external.transaction.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import momzzangseven.mztkbe.modules.web3.admin.application.dto.MarkTransactionSucceededResult;
import momzzangseven.mztkbe.modules.web3.transaction.application.dto.MarkTransactionSucceededCommand;
import momzzangseven.mztkbe.modules.web3.transaction.application.port.in.MarkTransactionSucceededUseCase;
import momzzangseven.mztkbe.modules.web3.transaction.domain.model.Web3TxStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MarkTransactionSucceededAdapterTest {

  @Mock private MarkTransactionSucceededUseCase markTransactionSucceededUseCase;

  private MarkTransactionSucceededAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new MarkTransactionSucceededAdapter(markTransactionSucceededUseCase);
  }

  @Test
  void markSucceeded_mapsCommandAndResult() {
    when(markTransactionSucceededUseCase.execute(any()))
        .thenReturn(
            new momzzangseven.mztkbe.modules.web3.transaction.application.dto
                .MarkTransactionSucceededResult(
                22L,
                Web3TxStatus.SUCCEEDED,
                Web3TxStatus.UNCONFIRMED,
                "0x" + "a".repeat(64),
                "https://explorer/tx/22"));

    MarkTransactionSucceededResult result =
        adapter.markSucceeded(
            1L, 22L, "0x" + "a".repeat(64), "https://explorer/tx/22", "manual proof", "support-1");

    ArgumentCaptor<MarkTransactionSucceededCommand> captor =
        ArgumentCaptor.forClass(MarkTransactionSucceededCommand.class);
    verify(markTransactionSucceededUseCase).execute(captor.capture());

    assertThat(captor.getValue().operatorId()).isEqualTo(1L);
    assertThat(captor.getValue().reason()).isEqualTo("manual proof");
    assertThat(result.status()).isEqualTo("SUCCEEDED");
    assertThat(result.previousStatus()).isEqualTo("UNCONFIRMED");
  }
}
