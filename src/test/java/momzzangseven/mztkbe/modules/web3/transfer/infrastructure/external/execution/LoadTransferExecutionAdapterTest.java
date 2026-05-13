package momzzangseven.mztkbe.modules.web3.transfer.infrastructure.external.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.GetExecutionIntentQuery;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.GetExecutionIntentResult;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.SignRequestUnavailableReason;
import momzzangseven.mztkbe.modules.web3.execution.application.port.in.GetExecutionIntentUseCase;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionActionType;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionIntentStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionMode;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionResourceStatus;
import momzzangseven.mztkbe.modules.web3.execution.domain.model.ExecutionResourceType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LoadTransferExecutionAdapterTest {

  @Mock private GetExecutionIntentUseCase getExecutionIntentUseCase;

  @Test
  void load_exposesSignRequestUnavailableReason() {
    LoadTransferExecutionAdapter adapter =
        new LoadTransferExecutionAdapter(getExecutionIntentUseCase);
    when(getExecutionIntentUseCase.execute(new GetExecutionIntentQuery(7L, "intent-1")))
        .thenReturn(
            new GetExecutionIntentResult(
                ExecutionResourceType.TRANSFER,
                "web3:TRANSFER_SEND:7:req-1",
                ExecutionResourceStatus.PENDING_EXECUTION,
                ExecutionActionType.TRANSFER_SEND,
                "0x" + "a".repeat(64),
                "{}",
                "intent-1",
                ExecutionIntentStatus.AWAITING_SIGNATURE,
                LocalDateTime.of(2026, 5, 13, 10, 5),
                1_778_646_300L,
                ExecutionMode.EIP7702,
                2,
                null,
                SignRequestUnavailableReason.EIP7702_DEADLINE_TOO_CLOSE,
                null,
                null,
                null));

    var result = adapter.load(7L, "intent-1");

    assertThat(result.signRequest()).isNull();
    assertThat(result.signRequestUnavailableReason()).isEqualTo("EIP7702_DEADLINE_TOO_CLOSE");
  }
}
