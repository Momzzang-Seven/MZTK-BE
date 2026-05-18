package momzzangseven.mztkbe.modules.web3.execution.infrastructure.external.eip7702;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.util.List;
import momzzangseven.mztkbe.modules.web3.eip7702.application.dto.Eip7702ExecutionBatchCall;
import momzzangseven.mztkbe.modules.web3.eip7702.application.port.in.ManageExecutionEip7702UseCase;
import momzzangseven.mztkbe.modules.web3.execution.application.port.out.ExecutionEip7702GatewayPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExecutionEip7702GatewayAdapter unit test")
class ExecutionEip7702GatewayAdapterTest {

  @Mock private ManageExecutionEip7702UseCase manageExecutionEip7702UseCase;

  @Test
  @DisplayName("encodeExecute maps calls, prepareId, deadline, and signature to EIP-7702 use case")
  void encodeExecute_mapsArgumentsToEip7702UseCase() {
    ExecutionEip7702GatewayAdapter adapter =
        new ExecutionEip7702GatewayAdapter(manageExecutionEip7702UseCase);
    BigInteger deadline = BigInteger.valueOf(1_700_000_000L);
    when(manageExecutionEip7702UseCase.encodeExecute(any(), eq("intent-123"), eq(deadline), any()))
        .thenReturn("0xencoded");

    String result =
        adapter.encodeExecute(
            List.of(
                new ExecutionEip7702GatewayPort.BatchCall(
                    "0x" + "1".repeat(40), BigInteger.ZERO, "0xdeadbeef"),
                new ExecutionEip7702GatewayPort.BatchCall(
                    "0x" + "2".repeat(40), BigInteger.valueOf(7), "0x1234")),
            "intent-123",
            deadline,
            "0x010203");

    assertThat(result).isEqualTo("0xencoded");
    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Eip7702ExecutionBatchCall>> callsCaptor =
        ArgumentCaptor.forClass(List.class);
    ArgumentCaptor<byte[]> signatureCaptor = ArgumentCaptor.forClass(byte[].class);
    verify(manageExecutionEip7702UseCase)
        .encodeExecute(
            callsCaptor.capture(), eq("intent-123"), eq(deadline), signatureCaptor.capture());

    List<Eip7702ExecutionBatchCall> calls = callsCaptor.getValue();
    assertThat(calls).hasSize(2);
    assertThat(calls.get(0).to()).isEqualTo("0x" + "1".repeat(40));
    assertThat(calls.get(0).value()).isEqualTo(BigInteger.ZERO);
    assertThat(calls.get(0).data())
        .containsExactly((byte) 0xde, (byte) 0xad, (byte) 0xbe, (byte) 0xef);
    assertThat(calls.get(1).to()).isEqualTo("0x" + "2".repeat(40));
    assertThat(calls.get(1).value()).isEqualTo(BigInteger.valueOf(7));
    assertThat(calls.get(1).data()).containsExactly((byte) 0x12, (byte) 0x34);
    assertThat(signatureCaptor.getValue()).containsExactly((byte) 1, (byte) 2, (byte) 3);
  }
}
