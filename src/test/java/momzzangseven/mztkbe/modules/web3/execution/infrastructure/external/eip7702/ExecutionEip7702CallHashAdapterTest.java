package momzzangseven.mztkbe.modules.web3.execution.infrastructure.external.eip7702;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.util.List;
import momzzangseven.mztkbe.modules.web3.eip7702.application.dto.Eip7702ExecutionBatchCall;
import momzzangseven.mztkbe.modules.web3.eip7702.application.port.in.ManageExecutionEip7702UseCase;
import momzzangseven.mztkbe.modules.web3.execution.application.dto.ExecutionDraftCall;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExecutionEip7702CallHashAdapter unit test")
class ExecutionEip7702CallHashAdapterTest {

  @Mock private ManageExecutionEip7702UseCase manageExecutionEip7702UseCase;

  @Test
  @DisplayName("hashCalls delegates canonical EIP-7702 calls after converting draft hex data")
  void hashCalls_convertsDraftCallsAndDelegatesToEip7702UseCase() {
    ExecutionEip7702CallHashAdapter adapter =
        new ExecutionEip7702CallHashAdapter(manageExecutionEip7702UseCase);
    when(manageExecutionEip7702UseCase.hashCalls(org.mockito.ArgumentMatchers.any()))
        .thenReturn("0x" + "f".repeat(64));

    String result =
        adapter.hashCalls(
            List.of(
                new ExecutionDraftCall("0x" + "1".repeat(40), BigInteger.ZERO, "0xdeadbeef"),
                new ExecutionDraftCall("0x" + "2".repeat(40), BigInteger.valueOf(7), "0x1234")));

    assertThat(result).isEqualTo("0x" + "f".repeat(64));
    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Eip7702ExecutionBatchCall>> callsCaptor =
        ArgumentCaptor.forClass(List.class);
    verify(manageExecutionEip7702UseCase).hashCalls(callsCaptor.capture());

    List<Eip7702ExecutionBatchCall> calls = callsCaptor.getValue();
    assertThat(calls).hasSize(2);
    assertThat(calls.get(0).to()).isEqualTo("0x" + "1".repeat(40));
    assertThat(calls.get(0).value()).isEqualTo(BigInteger.ZERO);
    assertThat(calls.get(0).data())
        .containsExactly((byte) 0xde, (byte) 0xad, (byte) 0xbe, (byte) 0xef);
    assertThat(calls.get(1).to()).isEqualTo("0x" + "2".repeat(40));
    assertThat(calls.get(1).value()).isEqualTo(BigInteger.valueOf(7));
    assertThat(calls.get(1).data()).containsExactly((byte) 0x12, (byte) 0x34);
  }
}
